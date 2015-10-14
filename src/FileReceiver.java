import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.zip.CRC32;

public class FileReceiver {
	
	private static final int PACKET_SIZE = 1000;
	
	private static byte[] sendData;
	private static byte[] receiveData;
	private static InetSocketAddress addr;
	private static DatagramSocket receiverSocket;
	private static BufferedOutputStream toFile;
	private static String message;
	private static DatagramPacket toReceive;
	private static DatagramPacket ack;
	private static File dest;
	private static boolean toACK;
	private static long rcvChksum;
	private static int rcvSeqNum;
	private static int rcvLen;
	private static byte[] data;
	
	public static void main(String[] args) throws Exception {
		
		if (args.length != 1) {
			System.err.println("Usage: FileReceiver <port>");
			System.exit(-1);
		}
		
		toACK = false;
		receiverSocket = new DatagramSocket(Integer.parseInt(args[0]));
		sendData = new byte[PACKET_SIZE];
		receiveData = new byte[PACKET_SIZE];
		dest = null;
		toFile = null;
		
		while(true){
		
			//---------------			Receiving       ---------------------//
			toReceive = new DatagramPacket(receiveData, receiveData.length);
			receiverSocket.receive(toReceive);
			
			//---------------			Checking       ---------------------//
			//index for splitting header and data
			int splitter = 0;
			for(int i = 0; i < toReceive.getLength() - 1; i++){
				if(receiveData[i] == '\n' && receiveData[i+1] =='\n'){
					splitter = i;
					break;
				}
			}
			
			//getting header
			if(splitter != 0){
			
				byte[] byteHeader = new byte[splitter + 1];
				System.arraycopy(receiveData, 0, byteHeader, 0, splitter + 1);
				String[] header = new String(byteHeader).split("\n");
				System.out.println(Arrays.toString(header));
				

				try{
					//initializing header variables
					rcvChksum = Long.parseLong(header[0]);
					rcvSeqNum = Integer.parseInt(header[1]);
					rcvLen = Integer.parseInt(header[2]);
					System.out.println("Length: " + rcvLen);
				
				
					//Checking checksum
					int chksumHeaderLen = header[0].length() + 1;	//+1 for removed '\n'
					System.out.println("Checksum Length: " + chksumHeaderLen);
					byte[] byteToCheck = new byte[toReceive.getLength()- chksumHeaderLen]; 
					System.arraycopy(receiveData, chksumHeaderLen, byteToCheck, 0, toReceive.getLength() - chksumHeaderLen);
					
					CRC32 checksum = new CRC32();
					checksum.update(byteToCheck);
					long calChksum = checksum.getValue();
					
					System.out.println("CAL: " + calChksum);
					System.out.println("RCV: " + rcvChksum);
					
					if(calChksum == rcvChksum){
						toACK = true;
					}
				
				} catch (Exception e){
					//Do nothing --> toACK already false
					//No need to write because if can't parse means corrupted/wrong already
				}
			}
		
			
			//---------------			Responding       ---------------------//
			addr = (InetSocketAddress) toReceive.getSocketAddress();
			
			String response = "";
			if(toACK){
				if(rcvSeqNum == -1){
					response = "END";
				} else {
					response = "ACK" + rcvSeqNum;
				}
				
			} else {
				response = "NAK";
				//temp, may want to implement buffer to replace in future
			}
			
			System.out.println("Sending response: " + response);
			sendData = response.getBytes();
			ack = addHeader(sendData);
			receiverSocket.send(ack);
			
			//---------------			Writing       ---------------------//
			//getting data
			
			if(toACK){
				data = new byte[rcvLen];
				System.arraycopy(receiveData, splitter + 2, data, 0, rcvLen);
				
				message = new String(data);								//log
				System.out.println("Received packet " + rcvSeqNum);
				System.out.println("Received msg: " + message); 		//log
				
				if(rcvSeqNum == -1){
					System.out.println("End writing to " + dest.getName());
					toFile.close();
				} else if(rcvSeqNum == 0) {	
					//get the first entry (fileName)
						dest = new File(message.trim());
						toFile = new BufferedOutputStream(new FileOutputStream(dest));
				} else {
					System.out.println("Writing: " + message);
					toFile.write(data, 0, rcvLen);
					toFile.flush();
				}
			}
		
			//Clearing current contents
			receiveData = new byte[PACKET_SIZE];
			sendData = new byte[PACKET_SIZE];
			toACK = false;
		}
	}
	
	private static DatagramPacket addHeader(byte[] msg){
		
		//checksum of message
		CRC32 checksum = new CRC32();
		checksum.update(msg);
		long pcktChksum = checksum.getValue();
		byte[] bytePcktChksum = (pcktChksum + "\n").getBytes();
		
		//add checksum
		byte[] pckt = new byte[msg.length + bytePcktChksum.length];
		System.arraycopy(bytePcktChksum, 0, pckt, 0, bytePcktChksum.length);
		System.arraycopy(msg, 0, pckt, bytePcktChksum.length, msg.length);	
		
		return new DatagramPacket(pckt, pckt.length, addr);
		
	}
}
