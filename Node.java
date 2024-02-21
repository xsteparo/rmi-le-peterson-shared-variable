import java.io.BufferedWriter;
import java.io.FileWriter;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

class Node implements INode {
    Registry registry;

    BlockingDeque<INode> ring;
    Boolean active;
    INode leader;
    //queue for messages in peterson algorithm FOR ELECTION ONLY!!!!!
    BlockingQueue<INode> queue;
    String shared;
    BufferedWriter logger;

    public static void main(String[] args) throws RemoteException {
        String logfile = "log";
        String host = "";
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.getName().equals("eth0")) {
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress inetAddress = inetAddresses.nextElement();
                        if (inetAddress instanceof java.net.Inet4Address) {
                            host=inetAddress.getHostAddress();
                            break;
                        }
                    }
                }
            }
        }catch (Exception e) {
            host = "127.0.0.1";
        }
        System.setProperty("java.rmi.server.hostname", host);
        Node node = new Node(logfile);
        node.repl();
    }

    Node(String lfile) throws RemoteException {
        registry = LocateRegistry.createRegistry(0);
        registry.rebind("", (INode) UnicastRemoteObject.exportObject(this, 0));
        leader = export();

        ring = new LinkedBlockingDeque<INode>();
        ring.add(export());
        active = false;
        queue = new LinkedBlockingQueue<INode>();
        try {
            logger = new BufferedWriter(new FileWriter(lfile));
        } catch (Exception e) {
            logger = null;
        }
        log("Node started");

    }

    INode connect(String host, int port) {
        try {
            return (INode) LocateRegistry.getRegistry(host, port).lookup("");
        } catch (Exception e) {
            return null;
        }
    }

    INode export() {
        try {
            return (INode) registry.lookup("");
        } catch (Exception e) {
            return null;
        }
    }

    String endpoint(String remote) {

        Pattern pattern = Pattern.compile("endpoint:\\[(.*?)\\]");
        Matcher matcher = pattern.matcher(remote);
        if (matcher.find()) {
            // Extract and return the endpoint information
            String s = matcher.group(1);

            //remove endpoint:
            return s;
        } else {
            // Return null if the pattern is not found
            return null;
        }
    }

    public void check() throws RemoteException {
    }


    void join(String host, int port) {
        if (ring.size() > 1) {
            leave();
        }
        INode node = connect(host, port);
        if (node == null) {
            error("Could not connect");
            return;
        }
        if (node.equals(export())) {
            error("Cannot join self");
            return;
        }
        //
        try {
            leader = node.getLeader();
            BlockingDeque<INode> n = node.setNext(export());
            ring = n;
            checkRing(node, leader, new LinkedBlockingDeque<INode>(ring));
            notify("Sucess");
            log("REPL: Joined network with leader: " + endpoint(leader.toString()));
        } catch (Exception e) {
            error("Could not join");
            leader = export();
        }
    }

    void leave() {
        if (ring.size() == 1) {
            notify("Not in network");
            return;
        }
        Iterator<INode> it = ring.descendingIterator();
        it.next();
        while (it.hasNext()) {
            INode n = it.next();
            try {
                n.delNext(export());
                ring.remove(export());
                checkRing(n, leader, new LinkedBlockingDeque<INode>(ring));
                notify("excluded from network");
                if (leader.equals(export())) {
                    election();
                }
                break;
            } catch (Exception e) {
            }
        }
        leader = export();
        ring = new LinkedBlockingDeque<INode>();
        ring.add(export());
        notify("Sucess");
        log("REPL: Left network");
    }

    public BlockingDeque<INode> setNext(INode next) throws RemoteException {
        BlockingDeque<INode> nring = new LinkedBlockingDeque<INode>(ring);
        nring.addLast(next);
        ring.addFirst(next);
        return nring;
    }

    public void delNext(INode next) throws RemoteException {
        ring.remove(next);
    }

    public void checkRing(INode start, INode l, BlockingDeque<INode> r) {
        log("Checking ring in this node");
        ring = r;
        leader = l;
        while (!ring.isEmpty()) {
            if (start.equals(export()) || !ring.contains(start)) {
                return;
            }
            try {
                r = new LinkedBlockingDeque<INode>(ring);
                r.addLast(r.poll());
                ring.peek().checkRing(start, l, r);
                log("Ring is verified");
                break;
            } catch (Exception e) {
                ring.poll();
            }
        }
        log("Ring is broken");
    }

    INode next() {
        while (!ring.isEmpty()) {
            INode n = ring.peek();
            try {
                n.check();
                return n;
            } catch (Exception e) {
                ring.poll();
            }
        }
        ring.add(export());
        return export();
    }

    public INode getLeader() throws RemoteException {
        return leader;
    }

    public void send(INode msg) throws RemoteException {
        log("Election: Received message: " + endpoint(msg.toString()));
        queue.add(msg);
    }

    void election() {
        log("Starting election");
        List<Callable<INode>> tasks = new ArrayList<Callable<INode>>();

        for (INode n : ring) {
            notify("Election: trying node " + endpoint(n.toString()));
            tasks.add(() -> {
                try {
                    return n.peterson();
                } catch (Exception e) {
                    return null;
                }
            });
        }
        try {
            ExecutorService executor = Executors.newWorkStealingPool(tasks.size());
            INode res = executor.invokeAny(tasks);
            log("Election finished");
            log("Leader: " + endpoint(res.toString()));
            executor.shutdownNow();
        } catch (Exception e) {
            error("Election failed");
            e.printStackTrace();
        }

    }

    public INode peterson() throws RemoteException {
        try{
            log("Election: starting peterson");
            leader=export();
            active=true;
            while(true) {
                if( !active ) {
                    INode msg = queue.take();
                    log("Election: Received message as relay: "+endpoint(msg.toString()));
                    try {
                        INode n = next();
                        log("Election: relaying message to next "+endpoint(n.toString()));
                        n.send(msg);
                    }
                    catch (Exception e) {}
                } else {
                    try {
                        leader.check();
                    } catch (Exception e) {
                        log("Election: leader is dead");
                        leader = export();
                    }
                    next().send(leader);
                    log("Election: sent message to next "+endpoint(next().toString()));
                    INode n2=queue.take();
                    log("Election: Received message: "+endpoint(n2.toString()));
                    if(leader.equals(n2)) {
                        log("Election: leader is elected");
                        Iterator<INode> it = ring.descendingIterator();
                        it.next();
                        checkRing(it.next() ,leader,new LinkedBlockingDeque<INode>(ring));
                        return leader;
                    }else{
                        next().send(n2);
                        log("Election: sent recieved message to next node: "+endpoint(next().toString()));
                        INode n3=queue.take();
                        log("Election: Received second message: "+endpoint(n3.toString()));
                        if(better(n2,leader,n3)){
                            log("Election: chose better leader");
                            leader=n2;
                        }else{
                            log("Election: going into relay mode");
                            active=false;
                            //send(n3);
                        }
                    }
                }
            }
        }catch(InterruptedException e){
            log("Election: interrupted");
            return null;
        }
    }

    Boolean better(INode n1, INode n2, INode n3) {
        Integer i1 = n1.hashCode();
        Integer i2 = n2.hashCode();
        Integer i3 = n3.hashCode();
        return i1 > i2 && i1 > i3;
    }

    void read() {
        while (true) {
            try {
                shared = leader.loadShared();
                notify("Shared: " + shared);
                return;
            } catch (Exception e) {
                ring.remove(leader);
                for (INode n : ring) {
                    try {
                        n.delNext(leader);
                    } catch (Exception e2) {
                    }
                }
                election();
            }
        }
    }

    void write(String value) {
        while (true) {
            try {
                leader.storeShared(value);
                notify("Shared");
                return;
            } catch (Exception e) {
                ring.remove(leader);
                for (INode n : ring) {
                    try {
                        n.delNext(leader);
                    } catch (Exception e2) {
                    }
                }
                election();
            }
        }
    }

    public String loadShared() throws RemoteException {
        return shared;
    }

    public void storeShared(String value) throws RemoteException {
        shared = value;
        log("Shared modified: " + shared);
    }

    void repl() {
        Scanner scanner = new Scanner(System.in);
        print("Welcome to repl\n");
        info();
        while (true) {
            print("> ");
            String argsline = scanner.nextLine();
            String[] args = argsline.split(" ");
            //System.out.print("< ");
            try {
                switch (args[0]) {
                    case "join":
                        join(args[1], Integer.parseInt(args[2]));
                        break;
                    case "leave":
                        leave();
                        break;
                    case "read":
                        read();
                        break;
                    case "write":
                        String value = argsline.substring(args[0].length() + 1);
                        write(value);
                        break;
                    case "help":
                        help();
                        break;
                    case "info":
                        info();
                        break;
                    case "bye":
                        bye();
                        break;
                    default:
                        throw new Exception("unknown");
                }
            } catch (Exception e) {
                error("Wrong command");
                //e.printStackTrace();
            }
        }
    }

    void help() {
        String h = """
                # Supported commands are:
                  - join  <host> <port>:
                    Joins node to network with given name, host and port
                  - leave:
                    Leaves network
                  - read:
                    Reads shared value
                  - write <value>:
                    Writes shared value
                  - help:
                    Prints out list of available commands
                  - info:
                    Prints out node info
                  - bye:
                    urns off node""";
        notify(h);
    }

    void info() {
        notify("Node is running");
        notify("Address: " + endpoint(registry.toString()));
        notify("Leader: " + endpoint(leader.toString()));
        notify("Ring: " + String.join(" ", ring.stream().map(n -> endpoint(n.toString())).toArray(String[]::new)));
    }

    void bye() {
        leave();
        log("Node stopped");
        notify("Bye");
        print("");
        System.exit(0);
    }

    void notify(String s) {
        System.out.println("\u001B[32m" + s);
    }

    void error(String s) {
        System.out.println("\u001B[31m" + s);
    }

    void print(String s) {
        System.out.print("\u001B[0m" + s);
    }

    void log(String s) {
        try {
            String formattedTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            logger.write(formattedTimestamp+"-");
            logger.newLine();
            logger.flush();
        } catch (Exception e) {}
    }
}

interface INode extends Remote {
    void check() throws RemoteException;

    BlockingDeque<INode> setNext(INode next) throws RemoteException;

    void delNext(INode next) throws RemoteException;

    void checkRing(INode start, INode l, BlockingDeque<INode> r) throws RemoteException;

    INode getLeader() throws RemoteException;

    void send(INode msg) throws RemoteException;

    INode peterson() throws RemoteException;

    String loadShared() throws RemoteException;

    void storeShared(String value) throws RemoteException;
}
