package joliexx.docker.service;

import com.sun.istack.internal.NotNull;
import jolie.net.ports.OutputPort;
import jolie.runtime.*;
import jolie.runtime.embedding.RequestResponse;
import joliexx.docker.executor.DockerExecutor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class JolieDocker extends JavaService {

    private static final DockerExecutor docker = new DockerExecutor();

    /*
     * if tail is zero then the whole log is read
     */
    private String[] getLog( String containerName, boolean appendStderr, int tail ) throws FaultException {

        DockerExecutor.RunResults log;

        if ( tail == 0 ) {
            log = docker.executeDocker( false, "logs", containerName );
        } else {
            log = docker.executeDocker( false, "logs", "--tail=" + tail, containerName );
        }

        StringBuilder out = new StringBuilder();

        if ( !log.getStdout().trim().isEmpty() ) {
            out.append(log.getStdout());
        }

        if ( appendStderr && !log.getStderr().trim().isEmpty() ) {
            out.append(log.getStderr());
        }

        return out.toString().split( System.lineSeparator() );
    }

    /*
     * get the whole log without errors from the execution
     */
    private String[] getLog( String containerName ) throws FaultException {
        return getLog( containerName, false, 0 );
    }


    @RequestResponse
    public Value requestSandbox( Value request ) throws FaultException {
        Value response = Value.create();
        String fileName = request.getFirstChild( "filename" ).strValue();
        String containerName = request.getFirstChild( "containerName" ).strValue();
        Integer port = request.getFirstChild( "port" ).intValue();
        Boolean detach = request.getFirstChild( "detach" ).boolValue();

        String mountPoint = Paths.get( fileName ).toAbsolutePath().normalize().getParent().toString();
        String nameOnly = Paths.get( fileName ).getFileName().toString();

        String[] args;

        if ( detach ) {
            args = new String[] { "run", "-id", "--read-only", "--volume", mountPoint + ":/home/jolie:ro",
                    "-m", "256m", "--cpu-shares", "256", "--expose", port.toString() , "--name", containerName,
                    "ezbob/jolie:latest", nameOnly };

        } else {
            args = new String[] {
                    "run", "-i", "--rm", "--read-only", "--volume", mountPoint + ":/home/jolie:ro",
                    "-m", "256m", "--cpu-shares", "256", "--expose", port.toString() , "--name", containerName,
                    "ezbob/jolie:latest", nameOnly
            };
        }

        DockerExecutor.RunResults results = docker.executeDocker(false, args);

        if ( !results.getStderr().isEmpty() ) {
            response.setFirstChild("stderr", results.getStderr());
        }

        if ( !results.getStdout().isEmpty() ) {
            response.setFirstChild("stdout", results.getStdout());
        }

        response.setFirstChild("exitCode", results.getExitCode());

        return response;
    }

    @RequestResponse
    public Value haltSandbox( Value request ) throws FaultException {
        Value response = Value.create();
        String containerName = request.strValue();

        Integer exitCode = 0;
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        DockerExecutor.RunResults results;

        results = docker.executeDocker(false, "stop", containerName );

        stdout.append( results.getStdout().trim() );
        stderr.append( results.getStderr().trim() );

        exitCode = results.getExitCode();

        results = docker.executeDocker(false, "rm", containerName );

        stdout.append( System.lineSeparator() );
        stdout.append( results.getStdout().trim() );

        stderr.append( System.lineSeparator() );
        stderr.append( results.getStderr().trim() );

        if ( exitCode == 0 && results.getExitCode() != 0 ) {
            exitCode = results.getExitCode();
        }

        if ( !stdout.toString().trim().isEmpty() ) {
            response.setFirstChild("stdout", stdout.toString());
        }

        if ( !stderr.toString().trim().isEmpty() ) {
            response.setFirstChild("stderr", stderr.toString());
        }
        response.setFirstChild("exitCode", exitCode);

        return response;
    }

    @RequestResponse
    public Value getSandboxIP( Value request ) throws FaultException {

        Value response = Value.create();
        String containerName = request.strValue();

        DockerExecutor.RunResults results = docker.executeDocker( false,
                "inspect",
                "--format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'",
                containerName
        );

        DockerExecutor.RunResults portResults = docker.executeDocker(false,
                "inspect",
                "--format='{{range $p, $conf := .Config.ExposedPorts}} {{$p}} {{end}}'",
                containerName
        );

        String[] ports = portResults.getStdout().split("\n");
        String errors = results.getStderr() + "\n" + portResults.getStderr();

        if ( ports.length > 0 ) {
            for ( int i = 0; i < ports.length; ++i ) {
                ports[i] = ports[i].split("/")[0];
            }
            ValueVector vec = response.getChildren("ports");

            for ( int i = 0; i < ports.length; i++ ) {
                vec.add( Value.create( ports[i].trim() ) );
            }
        }

        if ( !results.getStdout().isEmpty() ) {
            response.setFirstChild( "ipAddress", results.getStdout() );
        }

        if ( !errors.isEmpty() ) {
            response.setFirstChild( "error", errors );
        }

        return response;
    }

    /**
     * We gotta ping the container and wait for it to be available,
     * else we got a connection refused
     */
    @RequestResponse
    public Value pingForAvailability( Value request ) throws FaultException {

        Value result = Value.create();
        Boolean printOut = request.getFirstChild("printInfo").boolValue();
        String ip = request.getFirstChild("ip").strValue();
        Integer port = request.getFirstChild("port").intValue();
        Integer tries = request.getFirstChild("attempts").intValue();

        Socket connection;

        int tried = 1;

        for (; tried < tries; ++tried ) {

            if ( printOut ) {
                System.out.println("#" + tried  + ": Connecting to " + ip + ":" + port);
            }

            try {
                connection = new Socket( ip, port );
                connection.close();
            } catch ( IOException ioe) {

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {}

                continue;
            }

            if ( printOut ) {
                System.out.println("#" + tried  + ": Connected to " + ip + ":" + port);
            }

            break;
        }

        if ( tried >= tries ) {
            if (printOut) {
                System.out.println( "Failed to connect to " + ip + ":" + port + ". Number of attempts exceeded. " );
            }
            result.setFirstChild( "isUp", false );
        } else {
            result.setFirstChild( "isUp", true );
        }

        return result;
    }

    /*
     * Check the logs for the alive signal
     */
    @RequestResponse
    public Value waitForSignal( Value request ) throws FaultException {

        Value result = Value.create();

        String containerName = request.getFirstChild("containerName").strValue();

        Integer tries = request.hasChildren( "attempts" ) ? request.getFirstChild( "attempts" ).intValue() : 1000;
        String signal = request.hasChildren( "signalMessage" ) ? request.getFirstChild( "signalMessage" ).strValue() : "ALIVE";

        int tried = 1;
        boolean isAlive = false;

        for (; tried < tries; ++tried ) {

            String[] logLines = getLog( containerName, false, 1 );
            for ( String line : logLines ) {
                if ( line.equals( signal ) ) {
                    isAlive = true;
                    break;
                }
            }
            if ( isAlive ) {
                break;
            }
        }

        result.setFirstChild("isAlive", isAlive);

        return result;
    }


    @RequestResponse
    public Value getLog( Value request ) throws FaultException {

        Value result = Value.create();

        DockerExecutor.RunResults log;
        if ( request.hasChildren( "tail" ) ) {

            log = docker.executeDocker( false,
                    "logs", "--tail=" + request.getFirstChild( "tail" ).intValue(), request.strValue()
            );

        } else {

            log = docker.executeDocker( false,
                    "logs", request.strValue()
            );
        }

        if ( !log.getStdout().isEmpty() ) {
            result.setFirstChild("log", log.getStdout());
        }

        if ( !log.getStderr().isEmpty() ) {
            result.setFirstChild("error", log.getStderr());
        }

        return result;
    }

}
