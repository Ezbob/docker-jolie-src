package joliexx.docker.service;

import com.sun.istack.internal.NotNull;
import jolie.runtime.FaultException;
import jolie.runtime.JavaService;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
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
                connection = new Socket(ip, port);
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
