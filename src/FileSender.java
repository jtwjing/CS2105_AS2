import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.zip.CRC32;

public class FileSender {
	
	private static final int PACKET_DATA_SIZE = 950;
	
	private static byte[] sendData;
	private static byte[] receiveData;
	private static File source;
	private static InetSocketAddress addr;
	private static DatagramSocket senderSocket;
	private static BufferedInputStream fromFile;
	private static int readLen;
	private static DatagramPacket toSend;
	private static DatagramPacket ack;
	private static int numPackets;
	private static int seqNum;
	private static byte[] byteToCheck; 
	
	public static void main(String[] args) throws Exception {
		
		long startTime = System.nanoTime();
		
		if (args.length != 4) {
			System.err.println("Usage: FileSender <host> <port> <sourceFile> <destFile>");
			System.exit(-1);
		}
		
		addr = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
		source = new File(args[2]);
		senderSocket = new DatagramSocket();
		sendData = new byte[PACKET_DATA_SIZE];
		receiveData = new byte[PACKET_DATA_SIZE];
		
		//determine number of packets needed
		numPackets = (int) Math.ceil(source.length()/PACKET_DATA_SIZE);
		System.out.println("Size: " + source.length());
		System.out.println("Num Packets: " + numPackets);
		
		//initialize to 0 for sending header
		seqNum = 0;
		
		//sending name of destFile
		sendData = args[3].getBytes();
		toSend = addHeader(seqNum, sendData, sendData.length);
		System.out.println("Sending packet 0 (Header)");
		senderSocket.send(toSend);

		//checking if need to re-send
		verifyReply();
		seqNum++;
		
		//Clearing send
		sendData = new byte[PACKET_DATA_SIZE];
				
		//Reader
		fromFile = new BufferedInputStream(new FileInputStream(source));
		
		//sending contents of sourceFile
		while((readLen = fromFile.read(sendData)) != -1){
			
			//create a packet with header
//			System.out.println("Length: " + readLen);					//
			toSend = addHeader(seqNum, sendData, readLen);
			System.out.println("Sending packet " + seqNum);
			
			//send packet
			senderSocket.send(toSend);
			
			//Response part
			verifyReply();
			seqNum++;
			
			//Clearing read + send
			sendData = new byte[PACKET_DATA_SIZE];
			receiveData = new byte[PACKET_DATA_SIZE];
		}
		
		//sending end response
		sendData = "END".getBytes();
		seqNum = -1;
		toSend = addHeader(seqNum, sendData, 3);
		System.out.println("Sending terminating packet");
		senderSocket.send(toSend);
		
		verifyReply();
		/*change this
		while(true){
			ack = new DatagramPacket(receiveData, receiveData.length);
			senderSocket.receive(ack);
			String ackMessage = new String(ack.getData());
			System.out.println(ackMessage);
			if(ackMessage.trim().contains("END")){
				break;
			}
			
			//clearing current content
			receiveData = new byte[PACKET_DATA_SIZE];
		}
		 */
		
		senderSocket.close();
		long endTime = System.nanoTime();
		System.out.println("SENDING TOOK " +(endTime-startTime) + "ns");
		
	}

	private static void verifyReply() throws IOException {
		boolean ackLast = false;
		while(!ackLast){
			
			//get the response from receiver
			ack = new DatagramPacket(receiveData, receiveData.length);
			try {
				senderSocket.setSoTimeout(100);
				senderSocket.receive(ack);
				System.out.println("From Receiver: " + new String(ack.getData()));		//
				String[] ackMessage = new String(ack.getData()).split("\n");
				try {
					long rcvChksum = Long.parseLong(ackMessage[0]);
					
					//checking checksum
					int chksumLen = ackMessage[0].length() + 1;
					byteToCheck = new byte[ack.getLength() - chksumLen];
					System.arraycopy(receiveData, chksumLen, byteToCheck, 0, ack.getLength() - chksumLen);
					
					CRC32 checksum = new CRC32();
					checksum.update(byteToCheck);
					long calChksum = checksum.getValue();
					
					System.out.println("CAL: " + calChksum);
					System.out.println("RCV: " + rcvChksum);
					
					//checking correct seq num
					int rcvSeqNum = Integer.parseInt(ackMessage[2].trim());
					
					System.out.println("rcvSeqNum: " + rcvSeqNum);
					System.out.println("seqNum: " + seqNum);
					
					//check checksum and correct sequence number 
					if(calChksum == rcvChksum && seqNum == rcvSeqNum){
						ackLast = true;
					} else {
						System.out.println("--- Resending Checksum/SeqNum failed---");					//
//						System.out.println(new String(toSend.getData()));			//
						senderSocket.send(toSend);
					}
					
					//clearing current content
					receiveData = new byte[PACKET_DATA_SIZE];
				} catch (Exception e){
					//if cannot parse the checksum means corrupted therefore re-send
					System.out.println("--- Resending Wrong---");					//
//					System.out.println(new String(toSend.getData()));			//
					senderSocket.send(toSend);
				}
			} catch (SocketTimeoutException e){
				System.out.println("--- Resending: Timeout ---");					//
//				System.out.println(new String(toSend.getData()));			//
				senderSocket.send(toSend);
			}
		}
	}
	
	//Add sequence number, length, chksum, header chksum to msg
	private static DatagramPacket addHeader(int seqNum, byte[] msg, int len){
		
		//seqNum header
		byte[] byteSeqNum;
		byteSeqNum = (seqNum + "\n").getBytes();
		
		//msg length header + header end
		byte[] byteLen;
		byteLen = (len + "\n\n").getBytes();
//		System.out.println("Msg Length: " + len);							//
//		System.out.println("Msg: " + new String(msg));						//
		
		//create packet without checksum
		byte[] tempPckt = new byte[byteSeqNum.length + byteLen.length + msg.length];
		System.arraycopy(byteSeqNum, 0, tempPckt, 0, byteSeqNum.length);
		System.arraycopy(byteLen, 0, tempPckt, byteSeqNum.length, byteLen.length);
		System.arraycopy(msg, 0, tempPckt, byteSeqNum.length + byteLen.length, len);
		
		//checksum of temp packet
		CRC32 checksum = new CRC32();
		checksum.update(tempPckt);
		long pcktChksum = checksum.getValue();
		byte[] bytePcktChksum = (pcktChksum + "\n").getBytes();
		
		//add checksum
		byte[] pckt = new byte[tempPckt.length + bytePcktChksum.length];
//		System.out.println("Checksum length: " + bytePcktChksum.length);		//
		System.arraycopy(bytePcktChksum, 0, pckt, 0, bytePcktChksum.length);
		System.arraycopy(tempPckt, 0, pckt, bytePcktChksum.length, tempPckt.length);
		
		return new DatagramPacket(pckt, pckt.length, addr);
	}
}