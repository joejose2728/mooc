#-------------------------------------------------------------------------------
# Name:        Client
# Purpose:
#
# Author:      Joel
#
# Created:     27/03/2014
# Copyright:   (c) Joel 2014
# Licence:     <your licence>
#-------------------------------------------------------------------------------
from SocketChannel import SocketChannel, SocketChannelFactory
from comm_pb2 import Request, Header, JobOperation, JobDesc, NameValueSet

class MoocClient():

  def __init__(self):
    self.channelFactory = SocketChannelFactory()

  def validateMessage(self, protobufMsg):
    '''
    Check the protobuf message
    '''
    if not protobufMsg.IsInitialized():
        raise Exception("Invalid message")

  def connect(self, host="localhost", port=5570):
    self.channel = self.channelFactory.openChannel(host,port)

  def close(self):
    if self.channel:
        self.channel.close()

  def send(self, request):
    # Check if channel is connected
    if not self.channel.connected:
        openChannel()

    self.channel.write(request.SerializeToString())

  def makeSignUpRequest(self):
    request = Request()
    request.header.originator = "client"
    request.header.routing_id = Header.JOBS

    request.body.job_op.action = JobOperation.ADDJOB
    request.body.job_op.data.name_space = "sign_up"
    request.body.job_op.data.owner_id = 8888
    request.body.job_op.data.job_id = "signup"
    request.body.job_op.data.status = JobDesc.JOBUNKNOWN

    data = request.body.job_op.data
    data.options.node_type = NameValueSet.VALUE
    data.options.name = "email"
    data.options.value = "joe.jose2706@gmail.com"

    self.send(request)

  def makePingRequest(self):
    request = Request()
    request.header.originator = "client"
    request.header.routing_id = Header.PING

    request.body.ping.number = 3
    request.body.ping.tag = "python-client"

    self.send(request)
