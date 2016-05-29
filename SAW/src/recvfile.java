
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 *
 * @author Karayel
 */
public class recvfile {
//    http://www2.ic.uff.br/~michael/kr1999/3-transport/3_040-principles_rdt.htm

    public static void main(String[] args) {
        try {
            if (!args[0].equals("-p")) {
                System.out.println("Wrong command line syntax ! ");
                System.exit(1);
            }
            // Take port number and convert it to integer.
            int port = Integer.valueOf(args[1]);
            //Create datagram socket
            DatagramSocket server = new DatagramSocket(port);
            InetAddress IPAddress;

            int senderPort;
            // DatagramPacket for received Packet and ACKed packet
            DatagramPacket receivePacket = null;
            DatagramPacket ackPacket = null;

            FileOutputStream fileOuputStream = null;

            System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [bound] " + port);
            // Create some variables
            int seqNumberInt = 0, count = 0, startFileOffset = 0, lastTakenSeqNumber = -1, cheksumControl;

            byte[] seqNumber, sData;
            byte[] receiveData;
            byte[] checksum = new byte[2];
            byte[] data = new byte[1020];

            while (true) {
                receiveData = new byte[1024];
                //Receive Data from the server
                receivePacket = new DatagramPacket(receiveData, receiveData.length);
                server.receive(receivePacket);
                // Get server IPAddress and port number
                IPAddress = receivePacket.getAddress();
                senderPort = receivePacket.getPort();
                // Create string for taken data
                String s = new String(receiveData);
                // Get sequence number, checksum and data part from the taken data
                seqNumber = getSeqNumber(receivePacket.getData());
                checksum = getCheckSum(receivePacket.getData());
                data = getData(receivePacket.getData());
                // Calculate sequence number
                seqNumberInt = seqNumber[0] * 128 + seqNumber[1];
                // Calculate integer checksum value
                int checkSumInt = getCheckSumValue(checksum);
                // Add sequnce number and data for calculating control checksum
                sData = addSequenceNumToData(seqNumber, data);
                // Calculate checksum control value
                cheksumControl = getCheckSumControl(sData);
                if (s.contains("Close_-_")) {
                    System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [Transfer close.] ");
                    byte[] finalData = createACK(seqNumber, 1);
                    // Send ACK to server for close program
                    ackPacket = new DatagramPacket(finalData, finalData.length, IPAddress, senderPort);
                    server.send(ackPacket);

                    System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [send ACK " + (seqNumberInt) + " ]");
                    break;
                } else {
                    // 1111111111111111 == 65535 
                    if (checkSumInt + cheksumControl == 65535) {
                        // If next packet comes, process the packet
                        if (lastTakenSeqNumber + 1 == seqNumberInt) {
                            // First packet includes file information
                            if (count == 0) {
                                // Create file according to server information
                                String filePath = getFilePath(new String(data));
                                fileOuputStream = new FileOutputStream(filePath);
                                System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [file create] " + filePath);
                                count++;
                            } else {
                                // Write data to file
                                fileOuputStream.write(data);

                                System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [recv data] "
                                        + startFileOffset + "(" + data.length + ") ACCEPTEN (in-order)");
                                startFileOffset += data.length;
                            }
                            lastTakenSeqNumber = seqNumberInt;
                        } else {
                            System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [recv data] "
                                    + startFileOffset + "(" + data.length + ") IGNORED (out-of-order)");
                        }
                        // Send ACK for Server
                        byte[] finalData = createACK(seqNumber, 1);
                        ackPacket = new DatagramPacket(finalData, finalData.length, IPAddress, senderPort);
                        server.send(ackPacket);
                        System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [send ACK " + seqNumberInt + "]");
                    } else {
                        System.out.println("<" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "> [recv corrupt packet] ");
                    }
                }
            }
            fileOuputStream.close();
        } catch (SocketException ex) {
            System.out.println(ex.getMessage());
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
    }

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

        System.arraycopy(sData, 2, result, 4, sData.length - 2);

        return result;
    }

    //</editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Get Data Information">
    /**
     *
     * Get checksum value
     *
     * @param data
     * @return
     */
    private static byte[] getCheckSum(byte[] data) {
        byte[] result = new byte[2];
        result[0] = data[2];
        result[1] = data[3];
        return result;
    }

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

    /**
     *
     * Get data
     *
     * @param data
     * @return
     */
    private static byte[] getData(byte[] data) {
        byte[] result = new byte[1020];

        for (int i = 0; i < result.length; i++) {
            result[i] = data[i + 4];
        }
        return result;
    }

    /**
     *
     * Get sequence number
     *
     * @param data
     * @return
     */
    private static byte[] getSeqNumber(byte[] data) {
        byte[] seq = new byte[2];
        seq[0] = data[0];
        seq[1] = data[1];
        return seq;
    }

    /**
     *
     * Get File Path
     *
     * @param s
     * @return
     */
    private static String getFilePath(String s) {
        String homePath = System.getProperty("user.home");
        String desktopPath = homePath + "\\Desktop";
        String fileInfo = s.split(":")[1];
        String filePath = desktopPath + "\\transfered_" + fileInfo;
        return filePath;
    }

    //</editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Convertion Operations">
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
     * Convert binary array to integer
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
     * Convert data binary to integer
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

    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Merge Operations">
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

    /**
     *
     * Merge two byte array
     *
     * @param c
     * @param d
     * @return
     */
    private static byte[] mergeTwoByteArray(byte[] c, byte[] d) {
        byte[] result = new byte[c.length + d.length];
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

   // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Create ACK">
    /**
     *
     * Create ACK message
     *
     * @param seqNum
     * @param i
     * @return
     */
    private static byte[] createACK(byte[] seqNum, int i) {
        byte[] aData = new byte[3];
        aData[0] = seqNum[0];
        aData[1] = seqNum[1];
        String ackData = i + "";
        aData[2] = ackData.getBytes()[0];
        byte[] checkSum = calculateCheckSum(aData);
        byte[] finalData = addCheckSumValue(aData, checkSum);
        return finalData;
    }

    //</editor-fold>
}
