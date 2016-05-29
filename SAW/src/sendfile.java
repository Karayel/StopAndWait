
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * http://www.thegeekstuff.com/2012/05/ip-header-checksum/
 * https://en.wikipedia.org/wiki/Signed_number_representations
 *
 * @author Karayel
 */
public class sendfile {

    public static void main(String[] args) {

        /*
         sendfile -r <recv host>:<recv port> -f <filename>
         <recv host> The IP address of the remote host in a.b.c.d format
         <recv port> The UDP port of the remote host.
         <filename> The name of the file (with its full path) to send.
         */
        try {
            if (!args[0].equals("-r") && !args[2].equals("-f")) {
                System.out.println("Wrong command line syntax ! ");
                System.exit(1);
            }
            // Split args according to :
            String[] recvInfo = args[1].split(":");
            // Take IP address from the args[1]
            String IP_arg = recvInfo[0];
            // Change String IP_arg to InetAddress object
            InetAddress IP = InetAddress.getByName(IP_arg);
            // Take port number and convert it to integer.
            int port = Integer.valueOf(recvInfo[1]);
            // Take full file path
            String filePath = args[3];
            // Create client socket
            DatagramSocket client = new DatagramSocket();
            client.setSoTimeout(5000);
            // Read byte given file path
            File file = new File(filePath);
            FileInputStream fis = new FileInputStream(file);
            byte[] fileData = new byte[(int) file.length()];
            fis.read(fileData);
            fis.close();
            // Get file name
            String fileName = file.getName();

            byte[] seqNo = new byte[2];

            int startFileOffset = 0;

            // File Information Data
            String sendingFileExtansion = "File:" + fileName + ":";
            byte[] byteFileExtansion = sendingFileExtansion.getBytes();
            // Close File Data
            String closeFile = "Close_-_";
            byte[] byteCloseFile = closeFile.getBytes();
            // Data Parts
            List<byte[]> dataList = divideArray(fileData, 1020);
            // Reorder data parts according to File Info > Data > Close
            List<byte[]> data = reorderDataList(dataList, byteFileExtansion, byteCloseFile);

            // Sending Data Packet
            for (int i = 0; i < data.size(); i++) {
                Integer seqNumInt = i;
                //Finding sequence number --> [ seqNumber / 128 , seqNumber % 128 ] 
                seqNo[0] = (byte) (i / 128);
                seqNo[1] = (byte) (i % 128);
                // Generate final data that is include sequence number, checksum and data
                byte[] sData = addSequenceNumToData(seqNo, data.get(i));
                byte[] checkSum = calculateCheckSum(sData);
                byte[] finalData = addCheckSumValue(sData, checkSum);
                // Send final data to given IP address and port
                DatagramPacket dataPacket = new DatagramPacket(finalData, finalData.length, IP, port);
                client.send(dataPacket);

                System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [send data] "
                        + startFileOffset + "(" + data.get(i).length + ")");
                while (true) {
                    try {
                        byte[] receiveData = new byte[1024];
                        //Receive ACK from the client
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        client.receive(receivePacket);
                        //Split ACK data
                        byte[] ackSeqNo = getACKSeqNumber(receivePacket.getData());
                        byte[] ackDataOK = getACKDataOK(receivePacket.getData());
                        byte[] ackCheckSum = getACKCheckSum(receivePacket.getData());
                        byte[] ackData = addACKSeqNoToData(ackSeqNo, ackDataOK);
                        // Create cheksum field
                        int checkSumValue = getCheckSumValue(ackCheckSum);
                        int cheksumControl = getCheckSumControl(ackData);
                        // 1111111111111111 == 65535 
                        if (checkSumValue + cheksumControl == 65535) {
                            // seqNumber formula = 128 * (seqNumber / 128) + seqNumber % 128;
                            int seqNumber = ackSeqNo[0] * 128 + ackSeqNo[1];
                            if (seqNumber == seqNumInt) {
                                startFileOffset += data.get(i).length;
                                System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [receive ACK " + seqNumber + "]");
                                break;
                            } else {
                                //Resend last data
                                client.send(dataPacket);
                                System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [resend data] "
                                        + startFileOffset + "(" + data.get(i).length + ")");
                            }
                        }
                    } catch (SocketTimeoutException ex) {
                        if (i == data.size() - 1) {
                            System.exit(0);
                        }
                        // Timeout Resend last data
                        System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [timeout resend data] "
                                + startFileOffset + "(" + data.get(i).length + ")");
                        client.send(dataPacket);
                    }
                }
            }
            System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [Transfer done.] " + port);
        } catch (SocketException | UnknownHostException ex) {
            System.out.println(ex.getMessage());
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
    }

    // <editor-fold defaultstate="collapsed" desc="Creating Data Chunks">
    /**
     *
     * Divide file byte given chunk size
     *
     * @param source
     * @param chunksize
     * @return
     */
    public static List<byte[]> divideArray(byte[] source, int chunksize) {

        List<byte[]> result = new ArrayList<byte[]>();
        int start = 0;
        while (start < source.length) {
            int end = Math.min(source.length, start + chunksize);
            result.add(Arrays.copyOfRange(source, start, end));
            start += chunksize;
        }

        return result;
    }

    /**
     *
     * Reorder Data List according to File Information > Data > Close
     *
     * @param dataList
     * @param byteFileExtansion
     * @param byteCloseFile
     * @return
     */
    private static List<byte[]> reorderDataList(List<byte[]> dataList, byte[] byteFileExtansion, byte[] byteCloseFile) {
        List<byte[]> result = new ArrayList<>();
        result.add(byteFileExtansion);
        for (int i = 0; i < dataList.size(); i++) {
            result.add(dataList.get(i));
        }
        result.add(byteCloseFile);

        return result;

    }

    //</editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Creating Data">
    /**
     *
     * Add Sequence Number to Data
     *
     * @param seqNo
     * @param data
     * @return
     */
    private static byte[] addSequenceNumToData(byte[] seqNo, byte[] data) {
        //Değiştir.
        byte[] result = new byte[data.length + 2];

        result[0] = seqNo[0];
        result[1] = seqNo[1];

        for (int i = 0; i < data.length; i++) {
            result[i + 2] = data[i];
        }
        return result;
    }

    /**
     *
     * Add checksum value to sData that have sequence number and data
     *
     * @param sData
     * @param checkSum
     * @return
     */
    private static byte[] addCheckSumValue(byte[] sData, byte[] checkSum) {
        byte[] result = new byte[sData.length + checkSum.length];
        result[0] = sData[0];
        result[1] = sData[1];
        result[2] = checkSum[0];
        result[3] = checkSum[1];

        int count = 0;
        for (int i = 2; i < sData.length; i++) {
            result[4 + count] = sData[i];
            count++;
        }
        return result;
    }

    /**
     *
     * Add ACK sequence number to data
     *
     * @param ackSeqNo
     * @param ackDataOK
     * @return
     */
    private static byte[] addACKSeqNoToData(byte[] ackSeqNo, byte[] ackDataOK) {
        byte[] result = new byte[3];
        result[0] = ackSeqNo[0];
        result[1] = ackSeqNo[1];
        result[2] = ackDataOK[0];
        return result;
    }

    //</editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Get ACK Information">
    /**
     *
     * Get ACK seq number from given data
     *
     * @param data
     * @return
     */
    private static byte[] getACKSeqNumber(byte[] data) {
        byte[] result = new byte[2];
        result[0] = data[0];
        result[1] = data[1];
        return result;
    }

    /**
     *
     * Get ACK data from given data (ACK data : 1 or 0)
     *
     * @param data
     * @return
     */
    private static byte[] getACKDataOK(byte[] data) {
        byte[] result = new byte[1];
        result[0] = data[4];
        return result;
    }

    /**
     *
     * Get ACK checksum value from given data
     *
     * @param data
     * @return
     */
    private static byte[] getACKCheckSum(byte[] data) {
        byte[] result = new byte[2];
        result[0] = data[2];
        result[1] = data[3];
        return result;
    }

    //</editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="File Information">    
    /**
     *
     * Get file extension from given file
     *
     * @param file
     * @return
     */
    private static String getFileExtension(File file) {
        String name = file.getName();
        try {
            return name.substring(name.lastIndexOf(".") + 1);
        } catch (Exception e) {
            return "";
        }
    }

    //</editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Checksum">
    /**
     *
     * Calculate checksum value
     *
     * @param sData
     * @return
     */
    private static byte[] calculateCheckSum(byte[] sData) {
        ArrayList wordsList = convertWordList(sData);
        int sumValue = 0;
        for (int i = 0; i < wordsList.size(); i++) {
            sumValue += (int) (wordsList.get(i));
            if (sumValue > 65535) {
                int[] greaterValue = convertBinary(sumValue, 18);
                int[] convertedValue = convertIt16Bits(greaterValue);
                sumValue = binaryToInt(convertedValue) + 1;
            }
        }
        int checksumResult = 65535 - sumValue;

        byte[] result = new byte[2];
        int[] checksum = convertBinary(checksumResult, 16);
        int[] checksumLeft = {checksum[0], checksum[1], checksum[2], checksum[3], checksum[4], checksum[5], checksum[6], checksum[7]};
        int[] checksumRight = {checksum[8], checksum[9], checksum[10], checksum[11], checksum[12], checksum[13], checksum[14], checksum[15]};
        result[0] = (byte) binaryToInt(checksumLeft);
        result[1] = (byte) binaryToInt(checksumRight);

        return result;
    }

    /**
     *
     * Get checksum value
     *
     * @param checksum
     * @return
     */
    private static int getCheckSumValue(byte[] checksum) {
        int first = checksum[0];
        int second = checksum[1];

        int[] firstCheckSum = convertBinary(first, 8);
        int[] secondCheckSum = convertBinary(second, 8);
        int[] mergedCheckSum = mergeTwoIntArray(firstCheckSum, secondCheckSum);
        return binaryToInt(mergedCheckSum);
    }

    /**
     *
     * Get checksum control value
     *
     * @param sData
     * @return
     */
    private static int getCheckSumControl(byte[] sData) {
        ArrayList wordsList = convertWordList(sData);
        int sumValue = 0;
        for (int i = 0; i < wordsList.size(); i++) {
            sumValue += (int) (wordsList.get(i));
            if (sumValue > 65535) {
                int[] greaterValue = convertBinary(sumValue, 18);
                int[] convertedValue = convertIt16Bits(greaterValue);
                sumValue = binaryToInt(convertedValue) + 1;
            }
        }
        return sumValue;
    }

    //</editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Binary Operations">
    /**
     *
     * Convert data binary to integer
     *
     * @param binary
     * @return
     */
    public static int binaryToInt(int[] binary) {
        int result = 0;
        int power = binary.length - 1;
        for (int i = 0; i < binary.length; i++) {
            result += Math.pow(2, power) * binary[i];
            power--;
        }
        return result;
    }

    /**
     *
     * Convert word list given sData
     *
     * @param sData
     * @return
     */
    private static ArrayList convertWordList(byte[] sData) {
        ArrayList words = new ArrayList();
        for (int i = 0; i < sData.length; i += 2) {
            int[] first = convertBinary(sData[i], 8);
            int[] second;
            // Gelen Datanın packet size tek ise sorun çıkartıyor.
            if (i + 1 == sData.length) {
                second = new int[8];
            } else {
                second = convertBinary(sData[i + 1], 8);
            }
            words.add(binaryToInt(mergeTwoIntArray(first, second)));
        }
        return words;
    }

    /**
     *
     * Convert integer value to binary with given binary length
     *
     * @param num
     * @param size
     * @return
     */
    public static int[] convertBinary(int num, int size) {
        if (num < 0) {
            num = num + 256;
        }
        int binary[] = new int[size];
        int index = size - 1;
        while (num > 0) {
            binary[index--] = num % 2;
            num = num / 2;
        }
        return binary;
    }

    /**
     *
     * Convert data to 16 bits
     *
     * @param greaterValue
     * @return
     */
    public static int[] convertIt16Bits(int[] greaterValue) {
        int[] result = new int[16];
        for (int i = 0; i < result.length; i++) {
            result[i] = greaterValue[i + 2];
        }
        return result;
    }

    /**
     *
     * Merge two integer array
     *
     * @param c
     * @param d
     * @return
     */
    public static int[] mergeTwoIntArray(int[] c, int[] d) {
        int[] result = new int[c.length + d.length];
        for (int i = 0; i < c.length; i++) {
            result[i] = c[i];
        }
        int count = c.length;
        for (int i = 0; i < d.length; i++) {
            result[count] = d[i];
            count++;
        }
        return result;
    }

    //</editor-fold>
}
