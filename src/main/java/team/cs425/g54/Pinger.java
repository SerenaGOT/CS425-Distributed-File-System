package team.cs425.g54;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.logging.Logger;
import java.lang.InterruptedException;


public class Pinger extends Thread{
	
	public CopyOnWriteArrayList<Node> memberList = new CopyOnWriteArrayList<Node>();
	public CopyOnWriteArrayList<Node> groupList = new CopyOnWriteArrayList<Node>();
	
	public int myPort = 0;
	private static final int TIMEOUT = 5000;
	private boolean stopped = false;
	static Logger logger = Logger.getLogger("main.java.team.cs425.g54.Pinger");
	public Node myNode = new Node();
	int memberListSize = 3;
	
	public Pinger(Node node, int port, CopyOnWriteArrayList<Node> memberList, CopyOnWriteArrayList<Node> groupList ) {
		this.memberList = memberList;
		this.groupList = groupList;
		stopped = false;
		myPort = port;
		myNode = node;
	}
	
	public void showMembershipList() {
		for (int i=0;i<memberList.size();i++) {
			Node node=memberList.get(i);
			System.out.println("Member"+(i+1)+" :");
			System.out.println("Node ID:"+node.nodeID+", Node Address:"+node.nodeAddr+", Node Port:"+node.nodePort);
		}
	}

	
	private String packPingMsg() {
		JSONObject jsonObj = new JSONObject();
		try{
			jsonObj.put("type", "ping");	
			jsonObj.put("nodeID", myNode.nodeID);
			jsonObj.put("nodeAddr", myNode.nodeAddr);
			jsonObj.put("nodePort", myNode.nodePort);
		} catch (JSONException e){
			e.printStackTrace();
		}
		return jsonObj.toString();	
	}
	
	private String packDeleteMsg(Node node) {
		JSONObject jsonObj = new JSONObject();
		try{
			
			jsonObj.put("type", "delete");
			jsonObj.put("nodeID", node.nodeID);
			jsonObj.put("nodeAddr", node.nodeAddr);
			jsonObj.put("nodePort", node.nodePort);
		} catch (JSONException e){
			e.printStackTrace();
		}
		return jsonObj.toString();	
	}
	
	public void removeNodeFromMemberList(Node node){
		for (int i=0;i<memberList.size();i++){
			Node tmpNode=memberList.get(i);
			if(node.nodeID==tmpNode.nodeID && node.nodeAddr.equals(tmpNode.nodeAddr) && node.nodePort==tmpNode.nodePort){
				memberList.remove(i);
				return;
			}
		}
	}

	public void removeNodeFromGroupList(Node node){
		for (int i=0;i<groupList.size();i++){
			Node tmpNode=groupList.get(i);
			if(node.nodeID==tmpNode.nodeID && node.nodeAddr.equals(tmpNode.nodeAddr) && node.nodePort==tmpNode.nodePort){
				groupList.remove(i);
				return;
			}
		}
	}

	public int containsInstance(CopyOnWriteArrayList<Node> list, Node node) {
        for (int i=0;i<list.size();i++) {
            if (compareNode(node,list.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public boolean compareNode(Node n1,Node n2){
        if (n1.nodeID==n2.nodeID && n1.nodeAddr.equals(n2.nodeAddr) && n1.nodePort==n2.nodePort)
            return true;
        else
            return false;
    }


	public void renewMembershipList(){
		logger.info("renew member list...");
        int index = containsInstance(groupList,myNode);
        int selfIndex = index;
        memberList.clear();
        for(int i=0;i<memberListSize;i++){
            index = (index+1) % groupList.size();
            if(containsInstance(memberList,groupList.get(index))>=0 || selfIndex==index)
                break;
            else
                memberList.add(groupList.get(index));
        }
        logger.info("Node " +myNode.nodeID+ "finishing renew membership list");
        showMembershipList();
	}

	public void removeNode(Node node) {
		removeNodeFromMemberList(node);
		removeNodeFromGroupList(node);

		renewMembershipList();

		for (Node member : memberList) {
			try {
				DatagramSocket ds = new DatagramSocket();
				byte[] data = new byte[1024];
				String deleteMsg = packDeleteMsg(node);
				
				InetAddress address = InetAddress.getByName(member.nodeAddr);
				
				DatagramPacket dpSent= new DatagramPacket(deleteMsg.getBytes(),deleteMsg.length(),address,member.nodePort);	
				
				ds.send(dpSent);
				
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} 
			
		}
	}

	
	public void stopPinger() {
		stopped = true;
	}

	public void restartPinger(){
		stopped = false;
	}

	
	private void ping(Node node) throws IOException {
		logger.info("Pinging "+node.nodeID+"......");
		boolean receivedResponse = false;
		try {
			DatagramSocket ds = new DatagramSocket();
			ds.setSoTimeout(TIMEOUT);
			
			String pingMsg = packPingMsg();
			InetAddress address = InetAddress.getByName(node.nodeAddr);
			
			
			
			DatagramPacket dpSent= new DatagramPacket(pingMsg.getBytes(),pingMsg.length(),address,node.nodePort);	
			
			byte[] data = new byte[2048];
			
			DatagramPacket dpReceived = new DatagramPacket(data, 2048);
			
			ds.send(dpSent);
			
			ds.receive(dpReceived);
			receivedResponse = true;
			
			if(receivedResponse) {
				logger.info("Node "+node.nodeID+" is alive!");
			}
			ds.close();
			
		} catch(SocketTimeoutException e){
			logger.warning("Node "+node.nodeID+" Fails!");
			receivedResponse = false;
			removeNode(node);
		}catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	public void run() {
		int memPointer=-1;
		while(!Thread.currentThread().isInterrupted()) {
			try{
				if(memberList.size()>0 && !stopped){
					memPointer = (memPointer+1)%memberList.size();
					Node node = memberList.get(memPointer);
					try {
						ping(node);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				sleep(1000);
			} catch (InterruptedException e){
				e.printStackTrace();
			}
			
		}
	}
}
