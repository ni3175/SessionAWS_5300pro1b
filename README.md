# SessionAWS_5300pro1b
1)Description of the problem and solution.
It is a project which focus on back-end development.
The project will use AWS Elastic Beanstalk together with UDP networking, to build a distributed, 
scalable and fault-tolerant version of the website. AWS Elastic Beanstalk will be used to create 
and maintain a load-balanced set of Application Servers running Apache Tomcat Version 7. 
The servers will run Java Servlet (and/or JSP) code implementing your site, together with additional 
Java code implementing a distributed, fault-tolerant, in-memory session state database similar to SSM.
To run our project, you should go to http://url/Handler after deploying to Amazon Elastic Beanstalk. 
We implemented k-resilient in the project, you should modify k in data.Data.java.

2. Overall Structure
a) Design
• The session ID is generated from a session number appended to the local server's IP address.
This format guarantees that it will be unique even across multiple servers.
• Cookies contain the session IDs, so the server can access an existing session.
• Sessions are stored in a ConcurrentHashMap structure, keyed on session ID.
• Sessions are timed out after 10 minutes of inactivity.
• A cleanup daemon removes expired entries from the session table with a fixed time.
b) Cookie format
Cookie contains three parts:
<sessionID, versionNumber, locationMetadata>
where locationMetadata = SvrIP_1, SvrIP_2, ... SvrIP_(K+1) in k-resilient system. Cookie is delimited by # 
to separate the three parts.
e.g. session cookie:
2*54.187.102.227#3#54.187.102.227_54.187.83.210_54.187.102.183_54.187.66.5
c) RPC message
The format of call message is callID_OPERATIONCODE_sessionID_version. If there is no need for sessionID, version, 
it will use zero instead like this, callID_OPERATIONCODE_0_0.
The format of reply message is callID_Informationadded.
For sessionRead(), the reply message is callID_versionNum_message_expireTime_primaryAddress_localAddress.
For sessionWrite() and check(), reply message is callID.
For getView(), reply message is callID_View.
3. Source File
a) sessionManagement
The package contains three classes: Handler, Session and Cleaner.
• Handler
Handler is a servlet that controls the process of operation in server. When initializing, 
it starts threads PRCServer, GroupManagement and Cleaner.
￼￼The first time a user accesses the website, it will create a session and try to backup the session if possblle.When user does some operation (hit replace, refresh or logout), if the operation is not “logout”, it will call sessionRead() and sessionWrite() in RPCClient, create or update session and then respond to client accordingly.
• Session
Session is the class of session objects. It contains some public and static method that other classes can call to 
perform session related operations.
Session Class contains a static HashMap type “sessionPool” that stores the <sessionId, session> pairs.
• Cleaner
Cleaner is a separate thread that cleans expired sessions periodically. It will iterate the sessionPool and remove 
expired session every run_period.
b) rpc
RPCClient contains check(), sessionRead(), sessionWrite(), getView() , marshal() and unmarshal() functions. check(), 
sessionRead(), sessionWrite() and getView() have same communication mechanism using UDP.
The call message explained before is marshalled into a byte[] using marshal() function and sent as a UDP packet.
The client then waits for a response and unmarshals the reply message using unmarchsal() function. 
If the response has the appropriate callID, it processes the response.
RPCServer uses a well-known port 5300.It starts an independent thread to listen to the request from clients 
and when it receives a request, it extracts the OPERATIONCODE and processes the request accordingly. 
Finally, it returns a response to the RPC client.
c) data
Address object has an IP address and a port number of a Server. Data object contains the address and view of a Server.
d) groupMembership
Keep track of all servers in the group to implement gossip protocol.
• View
View is responsible for the view management in the servers. It has operations to perform on views like shrink, 
insert, remove, choose and union.
• Bootstrap
Bootstrap is basically used to contact with SimpleDB.
We use SimpleDB to store a list of servers’ IP address to use as a bootstrap view. 
The list is a string of all members separated by an underscore.
• GroupManagement
GroupManagement is responsible for updating the view in the servers. Each server occasionally 
read the View of another server from its own View and also contact with the bootstrap view stored in SimpleDB.
To avoid convoys, each server will choose a sleep time uniformly at random between gossip_secs/2 and gossip_secs*3/2.
First each server will randomly choose a server t from its own view. Then the server sends a RPC request to it. 
If a successful reply return t.View, the current view of t, this server unions its own View with t.View. If this server fails to receive a PRC reply, it deletes server t from its own View. Second each server will read and update bootstrap view. In this way, the Bootstrap View will eventually converge to a subset of the active servers.
4. Elastic Beanstalk setup procedure
As a .war file, the project can be deployed to a web server or platform such as Amazon's Elastic Beanstalk. Simply update the provided CS5300.war file onto the service and run it. Doing so will deploy the application to http://url/Handler.
Our Elastic Beanstalk setup procedure:
1) Create a new environment
2) Setting the container type and uploading .war file
3) After the Environment is created, go into 'Configuration'. Set the minimum number of instances to 4 for 3-resilient.
4) Modify the Security Group (in the EC2 console) to have all inbound UDP and HTTP connections on ports 0-65535 accessible from everywhere.
5) Test the Beanstalk instance of our code.
5. Implement Extra credit
For K-resilience, in the session read, there are two conditions.
On one hand, if session is stored locally, it will directly read it. One the other hand, if session is stored remotely, 
we send RPC requests concurrently to IP in the cookies, which are valid at the moment and wait for the first successful 
response and discard other response (if any). This approach minimizes latency but generates some unnecessary network traffic.
In the session write, we send concurrently multiple session write requests to servers and wait until it has K successful responses 
or all requests have been processed accordingly.
