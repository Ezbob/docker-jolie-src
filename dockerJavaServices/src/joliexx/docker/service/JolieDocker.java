package joliexx.docker.service;

import jolie.runtime.*;
import jolie.runtime.embedding.RequestResponse;
import jolie.runtime.typing.TypeCastingException;
import joliexx.executor.DockerExecutor;
import joliexx.executor.Executor;

import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.HashMap;

public class JolieDocker extends JavaService {

    private final String user_name = "jolie";
    private final String jolie_home = "/usr/lib/jolie";
    private final String cpu_shares = "256";
    private final String memory_limit = "256m";
    private final String user_workspace = "/home/jolie";

    /*
     * if tail is zero then the whole log is read
     */
    private synchronized String[] getLog( String containerName, boolean appendStderr, int tail ) throws FaultException {

        DockerExecutor.RunResults log;
        DockerExecutor docker = new DockerExecutor();

        if ( tail == 0 ) {
            log = docker.execute( false, "logs", containerName );
        } else {
            log = docker.execute( false, "logs", "--tail", Integer.toString( tail ), containerName );
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

    private Executor.RunResults createVolume(DockerExecutor executor, String volumeName )
            throws FaultException {
        return executor.execute(false, "volume", "create",
                "--opt", "type=tmpfs", "--opt", "device=tmpfs", "--opt", "o=size=2M,nodev,nosuid,noexec",
                "--name", volumeName);
    }

    private Executor.RunResults copyToContainer(DockerExecutor executor, String containerName, String src)
            throws FaultException {
        return executor.execute(false, "cp", src, containerName + ":" + user_workspace );
    }

    private Executor.RunResults executeJolie(DockerExecutor executor, String containerName, String input) throws FaultException {
        return executor.execute( false, "exec", containerName, "jolie", input);
    }

    private Integer addToResults(Executor.RunResults results, StringBuilder stdout, StringBuilder stderr) {
        stderr.append(results.getStderr()).append(System.lineSeparator());
        stdout.append(results.getStdout()).append(System.lineSeparator());
        return results.getExitCode();
    }

    @RequestResponse
    public Value requestSandbox( Value request ) throws FaultException {
        Value response = Value.create();
        String file;
        String containerName;
        Integer port, exitCode;

        exitCode = 0;
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        try {
            file = request.getFirstChild( "filename" ).strValueStrict();
            containerName = request.getFirstChild( "containerName" ).strValueStrict();
            port = request.getFirstChild( "port" ).intValueStrict();
        } catch (TypeCastingException tce) {
            throw new FaultException(tce);
        }

        String copyFile = Paths.get( file ).toAbsolutePath().normalize().toString();
        String nameOnly = Paths.get( file ).getFileName().toString();

        DockerExecutor docker = new DockerExecutor();
        String[] args;
        String volumeName = containerName + "_vol";

        // create a new tmpfs volume to hold our data
        exitCode += addToResults( createVolume(docker, volumeName), stdout, stderr );

        args = new String[] { "run", "-id", "--read-only", "--volume", volumeName + ":" + user_workspace,
                "-m", memory_limit, "--cpu-shares", cpu_shares, "--expose", port.toString() , "--name", containerName,
                "-u", user_name, "-w", user_workspace, "-e", "JOLIE_HOME=" + jolie_home,
                "ezbob/ubjoliebase:latest"
        };

        // create a new container
        exitCode += addToResults( docker.execute( false, args ), stdout, stderr );

        // copy the execution file over
        exitCode += addToResults( copyToContainer( docker, containerName, copyFile ), stdout, stderr );

        // copy the libraries over to the workspace if it's specified
        if ( request.hasChildren("lib") ) {
            exitCode += addToResults( copyToContainer(docker, containerName, request.getFirstChild("lib").strValue()), stdout, stderr );
        }

        // finally execute jolie in the container
        exitCode += addToResults( executeJolie( docker, containerName, nameOnly ), stdout, stderr);

        if ( stdout.length() > 0 ) {
            response.setFirstChild("stderr", stdout.toString());
        }

        if ( stderr.length() > 0 ) {
            response.setFirstChild("stdout", stderr.toString());
        }

        response.setFirstChild("exitCode", exitCode);

        return response;
    }

    @RequestResponse
    public Value haltSandbox( Value request ) throws FaultException {
        Value response = Value.create();
        String containerName = request.strValue();
        DockerExecutor docker = new DockerExecutor();

        Integer exitCode = 0;
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        DockerExecutor.RunResults results;

        results = docker.execute(false, "stop", containerName );

        stdout.append( results.getStdout().trim() );
        stderr.append( results.getStderr().trim() );

        exitCode = results.getExitCode();

        results = docker.execute(false, "rm", containerName );

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
        DockerExecutor docker = new DockerExecutor();

        DockerExecutor.RunResults results = docker.execute( false,
                "inspect",
                "--format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'",
                containerName
        );

        DockerExecutor.RunResults portResults = docker.execute(false,
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

    @RequestResponse
    public Value attach( Value req ) throws FaultException {

        String containerName;
        try {
            containerName = req.strValueStrict();
        } catch (TypeCastingException tpe) {
            throw new FaultException(tpe);
        }

        DockerExecutor docker = new DockerExecutor();

        docker.execute( true, "attach", containerName );

        return Value.create();
    }

    /*
     * Check the logs for the alive signal
     */
    @RequestResponse
    public Value waitForSignal( Value request ) throws FaultException {

        Value result = Value.create();

        String containerName;
        try {
            containerName = request.getFirstChild("containerName").strValueStrict();
        } catch ( TypeCastingException te) {
            throw new FaultException( new TypeCastingException("containerName required") );
        }
        Boolean printOut = request.getFirstChild("printInfo").boolValue();
        Integer tries = request.hasChildren( "attempts" ) ? request.getFirstChild( "attempts" ).intValue() : 1000;
        String signal = request.hasChildren( "signalMessage" ) ? request.getFirstChild( "signalMessage" ).strValue() : "ALIVE";

        int tried = 1;
        boolean isAlive = false;

        if ( printOut ) {
            System.out.println("Checking for ready signal...");
        }

        for (; tried < tries; ++tried ) {
            if ( printOut ) {
                System.out.println( "Attempt #" + tried );
            }

            String[] logLines = getLog( containerName, false, 0 );
            for ( String line : logLines ) {

                if ( line.trim().equals( signal ) ) {
                    if ( printOut ) {
                        System.out.println( "Signal found." );
                    }
                    isAlive = true;
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    throw new FaultException( ie );
                }
            }
            if ( isAlive ) {
                break;
            }
        }

        if ( tried >= tries ) {
            if ( printOut ) {
                System.out.println("Number of attempts exceeded. Signal not found.");
            }
        }

        result.setFirstChild( "isAlive", isAlive );

        return result;
    }


    @RequestResponse
    public Value getLog( Value request ) throws FaultException {

        Value result = Value.create();

        DockerExecutor.RunResults log;
        DockerExecutor docker = new DockerExecutor();

        if ( request.hasChildren( "tail" ) ) {

            log = docker.execute( false,
                    "logs", "--tail=" + request.getFirstChild( "tail" ).intValue(), request.strValue()
            );

        } else {

            log = docker.execute( false,
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
