package joliexx.docker;

import jolie.net.*;
import jolie.net.ports.OutputPort;
import jolie.runtime.*;
import jolie.runtime.embedding.JolieServiceLoader;
import jolie.runtime.embedding.RequestResponse;
import joliex.java.Callback;
import joliex.java.Service;
import joliex.java.ServiceFactory;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

public class Networking extends JavaService {

    private final static int LENGTH_IP_V4 = 4;

    private String findIPv4( Enumeration<InetAddress> addresses ) {
        String result = null;

        for ( InetAddress address : Collections.list( addresses ) ) {
            if ( address.getAddress().length == LENGTH_IP_V4 ) {
                result = address.getHostName();
            }
        }

        return result;
    }

    private String getHostIp( String dockerHostName ) throws FaultException {

        String netInterface = dockerHostName != null ? dockerHostName : "docker0";

        Enumeration<InetAddress> allAddresses;

        try {
            allAddresses = NetworkInterface.getByName( netInterface ).getInetAddresses();
        } catch ( SocketException se ) {
            throw new FaultException( se );
        }

        return findIPv4( allAddresses );
    }

    /**
     * Create a socket comm channel
     */
    private CommChannel createHostChannel( String hostIp, String portName, int connectionLimit ) throws FaultException {
        CommChannel commChannel = null;
        try {
            commChannel = new SocketCommChannelFactory(
                    new CommCore(
                            this.interpreter(),
                            connectionLimit
                    )
            ).createChannel(
                    new URI(hostIp
                    ),
                    new OutputPort(
                            this.interpreter(),
                            portName
                    )
            );
        } catch ( Exception e ) {
            throw new FaultException( e );
        }

        return commChannel;
    }

    @RequestResponse
    public Value signalHost( Value request ) throws FaultException {
        Value response = Value.create();

        String hostInterface;

        if ( request.hasChildren( "hostInterface" ) ) {
            hostInterface = request.getChildren( "hostInterface" ).first().strValue();
        } else {
            hostInterface = "docker0";
        }
        String hostIp = getHostIp( hostInterface );

        Value config = Value.create();

        Service jolieService;

        try {
            jolieService = new ServiceFactory().create( new URI(hostIp), "sodep", config);
        } catch ( Exception e ) {
            throw new FaultException( e );
        }

        Callback aliveCallBack = null; //TODO put something here!
        jolieService.callRequestResponse("signal", Value.create("I'm alive!"), aliveCallBack);

        return response;
    }

}
