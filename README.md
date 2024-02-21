# Statring program

Runs on Java 17. To launch use command: java Node.java <ip>
IP - should be public ip of this host. Uses local ip if no ip is specified
It launches repl(CLI), use help to show commands.

# Introduction

This is a simple implementation of a distributed system using Java RMI. The system is a ring of nodes that can communicate with each other. The nodes can join and leave the ring. The nodes can also elect a leader using Peterson's algorithm. The leader is responsible for a shared variable that can be read and written by the nodes. The system is implemented in a single Java file that can be compiled into jar file. The jar file can be run on multiple machines.

## Java RMI

Java RMI (Remote Method Invocation) is a Java API that performs remote method invocation, the object-oriented equivalent of remote procedure calls (RPC), with support for direct transfer of serialized Java classes and distributed garbage-collection. The RMI allows an object running in one Java virtual machine (VM) to invoke methods on an object running in another Java VM.
This project uses RMI to implement a distributed system. The nodes are implemented as Java objects that can be accessed remotely. The nodes can communicate with each other by invoking methods on each other. The nodes can also export themselves as remote objects that can be accessed by other nodes.

## Leader Election

Leader election is the process of designating a single process as the organizer of some task distributed among several computers (nodes). Before the task is begun, all network nodes are unaware which node will serve as the "leader," or coordinator, of the task. After a leader election algorithm has been run, however, each node throughout the network recognizes a particular, unique node as the task leader.
This project uses Peterson's algorithm to elect a leader. Peterson's algorithm is a leader election algorithm for a ring topology. The algorithm is based on the idea of a token that is passed around the ring. The token is passed around the ring until it reaches the node with the highest priority. The node with the highest priority becomes the leader. The algorithm is implemented as a method that is invoked by the nodes. The method returns the leader node.

### Peterson

Peterson's algorithm is a leader election algorithm for a ring topology. The algorithm is based on the idea of a token that is passed around the ring. The token is passed around the ring until it reaches the node with the highest priority. The node with the highest priority becomes the leader.

## Shared Variable

A shared variable is a variable that can be accessed by multiple processes. The shared variable is implemented as a Java object that can be accessed remotely. The shared variable can be read and written by the nodes.
This algorithm uses leader election to determine which node is responsible for the shared variable. Other nodes can read and write the shared variable by invoking methods on the leader node.

# Implementation

This implementation is based on separating layers of functionality into defirent chapters. Each chapter is a section in the orginal file. The chapters are:

## Node

This chapter implements the node functionality. The node is implemented as a Java object that can be accessed remotely. The node can communicate with other nodes by invoking methods on them. The node can also export itself as a remote object that can be accessed by other nodes.

### Main

Creates node and starts REPL.

### Construct

Constructs node and exports it.

### Connect

Connects to remote node by host and port.

### Export

Exports node. Used to create remote object of itself.

### Endpoint

Extracts endpoint string from remote object. It is unique identifier of remote object.

### Check

Does nothing. Used to check if node is alive.

## Output

This chapter implements the output functionality. The output is implemented as a set of methods that can be invoked by the nodes. The output methods can print messages in different colors.

### Notify

Prints message in green color.

### Error

Prints message in red color.

### Print

Prints message in default color.

### Logger

Prints message in log file.

## REPL

This chapter implements the REPL functionality. The REPL is implemented as a method that is invoked by main. The REPL is a loop that reads commands from the user and executes them. The REPL can execute commands that are implemented as methods in the Node class.

### Help

Prints out available commands.

### Info

Prints out information about current node

### Bye

Leave node

## Ring Stability

This chapter implements the ring stability functionality. The ring stability is managed by storing ordered ring with public interfaces of all known nodes. The ring is automatically fixed on node join and leave. method for getting next node can skip dead nodes.

### Join

Joins node to ring

### Leave

leaves ring

### Set Next

Ask remote node to add given node to ring. Ring for this node based on ring of remote node on success.

### Delete Next

Call remote node to delete given node from ring.

### CheckRing

Check if ring is broken. If it is broken try to fix it.

### Next

Get next node. If node is dead skip it.


## Elections

Nodes uses queue for sending election messages. Nodes can be in two states: active and passive. Active node sends messages to next node. Passive node waits for messages. When node receives message it can become active or passive. If node is active it sends message to next node. If node is passive it sends message to next node.


### Get leader

Remote getter for leader.


### Send message

Add message to remote message queue i.e send message to remote node.


### Start Election

Starts election. It starts a thread for each node in ring. Each thread thread tarts peterson algorithm on different remote node. When one of the threads finishes election is finished.

### Peterson

Peterson's algorithm is a leader election algorithm for a ring topology. The algorithm is based on the idea of a token that is passed around the ring. The token is passed around the ring until it reaches the node with the highest priority. The node with the highest priority becomes the leader.

1.  Better

    Compares three nodes and returns true if first node is better than other two.


## Shared Variable

This chapter implements the shared variable functionality. The shared variable is implemented as a Java object that can be accessed remotely. The shared variable can be read and written by the nodes.


### Read

Reads string data from shared variable


### Write

Writes string data to shared variable

### Load shared

Remote getter for shared variable.

### Store shared

Remote setter for shared variable.


