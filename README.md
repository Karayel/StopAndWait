# StopAndWait

<b>Sender Command Line Syntax</b>

The command line syntax for your sending is given below.The client program takes command line argument
of the remote IP address and port number, and the name of the file to transmit. The syntax for launching your
sending program is therefore:
sendfile -r <recv host>:<recv port> -f <filename>
<recv host> (Required) The IP address of the remote host in a.b.c.d format.
<recv port> (Required) The UDP port of the remote host.
<filename> (Required) The name of the file (with its full path) to send.
To aid in grading and debugging, your sending program should print out messages to the console: When a
sender sends a packet (including retransmission), it should print the following to standard error:

<b>Receive Command Line Syntax</b>

The command line syntax for your receiving program is given below.
recvfile -p <recv port>
<recv port> (Required) The UDP port to listen on.


<b> How Its Works! </b>

1. javac sendfile.java
2. javac recvfile.java
3. java recvfile -p 8282
4. java sendfile -r 127.0.0.1:8282 -f filePath

