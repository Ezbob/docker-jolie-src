package joliexx.docker.service;

import com.sun.istack.internal.NotNull;
import jolie.runtime.FaultException;
import jolie.runtime.JavaService;
import jolie.runtime.Value;
import jolie.runtime.embedding.RequestResponse;
import joliexx.docker.executor.DockerExecutor;

import java.nio.file.Paths;

public class JolieDocker extends JavaService {

    private static final DockerExecutor docker = new DockerExecutor();

    @RequestResponse
    public Value requestSandbox( Value request ) throws FaultException {
        Value response = Value.create();
        String fileName = request.getFirstChild( "filename" ).strValue();
        String containerName = request.getFirstChild( "containerName" ).strValue();
        Integer port = request.getFirstChild( "port" ).intValue();
        Boolean detach = request.getFirstChild( "detach" ).boolValue();

        String mountPoint = Paths.get( fileName ).toAbsolutePath().getParent().toString();
        String nameOnly = Paths.get( fileName ).getFileName().toString();

        String[] args;

        if ( detach ) {
            args = new String[] { "run", "-id", "--read-only", "--volume", mountPoint + ":/home/jolie:ro",
                    "-m", "256m", "--cpu-shares", "256", "--expose", port.toString() , "--name", containerName,
                    "ezbob/jolie:0.1.1", nameOnly };

        } else {
            args = new String[] {
                    "run", "-i", "--rm", "--read-only", "--volume", mountPoint + ":/home/jolie:ro",
                    "-m", "256m", "--cpu-shares", "256", "--expose", port.toString() , "--name", containerName,
                    "ezbob/jolie:0.1.1", nameOnly
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

        stdout.append( results.getStdout() );
        stderr.append( results.getStderr() );

        exitCode = results.getExitCode();

        results = docker.executeDocker(false, "rm", containerName );

        stdout.append( System.lineSeparator() );
        stdout.append( results.getStdout() );

        stderr.append( System.lineSeparator() );
        stderr.append( results.getStderr() );

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

        if ( !results.getStdout().trim().isEmpty() ) {
            response.setFirstChild("ipAddress", results.getStdout());
        }

        if ( !results.getStderr().trim().isEmpty() ) {
            response.setFirstChild("error", results.getStderr());
        }

        return response;
    }

}
