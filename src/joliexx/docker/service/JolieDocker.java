package joliexx.docker.service;

import com.sun.istack.internal.NotNull;
import jolie.runtime.FaultException;
import jolie.runtime.JavaService;
import jolie.runtime.Value;
import jolie.runtime.embedding.RequestResponse;

import java.io.*;
import java.nio.file.Paths;

public class JolieDocker extends JavaService {

    static String DOCKER_INVOCATION = "docker";

    private class RunResults {
        String stdout;
        String stderr;
        Integer exitCode;

        public RunResults(String stdout, String stderr, Integer exitCode) {
            this.stderr = stderr;
            this.stdout = stdout;
            this.exitCode = exitCode;
        }
    }

    private RunResults executeDocker(String... args) throws FaultException {

        try {

            String[] execArgs = new String[args.length + 1];

            System.arraycopy(args, 0, execArgs, 1, args.length );
            execArgs[0] = DOCKER_INVOCATION;

            Process process = new ProcessBuilder(
                    execArgs
            ).start();

            try {
                return new RunResults(readStream(process.getInputStream(), false),
                        readStream(process.getErrorStream(), true), process.waitFor());
            } catch (InterruptedException interruptedExcep) {
                throw new FaultException(interruptedExcep);
            }
        } catch (IOException ioException) {
            throw new FaultException(ioException);
        }
    }

    @RequestResponse
    public Value requestSandbox( Value request ) throws FaultException {
        Value response = Value.create();
        String fileName = request.getFirstChild( "filename" ).strValue();
        String containerName = request.getFirstChild( "containerName" ).strValue();
        Integer port = request.getFirstChild( "port" ).intValue();
        Boolean detach = request.getFirstChild( "detach" ).boolValue();

        String mountPoint = Paths.get( fileName ).toAbsolutePath().getParent().toString();
        String nameOnly = Paths.get( fileName ).getFileName().toString();

        try {
            Process process;
            if ( detach ) {
                process = new ProcessBuilder(
                        "docker", "run",
                        "-id", "--read-only", "--volume", mountPoint + ":/home/jolie:ro",
                        "-m", "256m", "--cpu-shares", "256", "--expose", port.toString() , "--name", containerName,
                        "ezbob/jolie:0.1.1", nameOnly
                ).start();
            } else {
                process = new ProcessBuilder(
                        "docker", "run",
                        "-i", "--rm", "--read-only", "--volume", mountPoint + ":/home/jolie:ro",
                        "-m", "256m", "--cpu-shares", "256", "--expose", port.toString() , "--name", containerName,
                        "ezbob/jolie:0.1.1", nameOnly
                ).start();
            }

            try {
                String stdout = readStream(process.getInputStream(), false);
                String stderr = readStream(process.getErrorStream(), true);

                Integer exitCode = process.waitFor();

                if ( !stderr.isEmpty() ) {
                    response.setFirstChild("stderr", stderr);
                }

                if ( !stdout.isEmpty() ) {
                    response.setFirstChild("stdout", stdout);
                }

                response.setFirstChild("exitCode", exitCode);

            } catch ( InterruptedException interruptedException ) {
                throw new FaultException( "process interrupted", interruptedException );
            }

        } catch ( IOException ioException ) {
            throw new FaultException( ioException );
        }

        return response;
    }

    @RequestResponse
    public Value haltSandbox( Value request ) throws FaultException {
        Value response = Value.create();

        String containerName = request.strValue();

        try {
            Integer exitCode = 0;
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Process process = new ProcessBuilder(
                    "docker", "stop", containerName
            ).start();

            stdout.append(readStream(process.getInputStream(), false));
            stderr.append(readStream(process.getErrorStream(), true));

            try {
                Integer code;
                if ( (code = process.waitFor()) != 0 ) {
                       exitCode = code;
                }
            } catch (InterruptedException interruptedException) {
                throw new FaultException(interruptedException);
            }

            process = new ProcessBuilder(
                    "docker", "rm", containerName
            ).start();

            stdout.append( readStream(process.getInputStream(),false) );
            stdout.append( readStream(process.getErrorStream(),true) );

            try {
                Integer code;
                if ( (code = process.waitFor()) != 0 ) {
                    exitCode = code;
                }
            } catch (InterruptedException interruptedException) {
                throw new FaultException(interruptedException);
            }

            response.setFirstChild("stderr", stderr.toString());
            response.setFirstChild("stdout", stdout.toString());
            response.setFirstChild("exitCode", exitCode);

        } catch ( IOException ioException ) {
            throw new FaultException( ioException );
        }

        return response;
    }

    @RequestResponse
    public Value getSandboxIP( Value request ) {

        return null;
    }

    private static String readStream( @NotNull InputStream stream, Boolean isErrorStream ) throws IOException {
        StringBuilder result = new StringBuilder();
        String line;

        BufferedReader reader = new BufferedReader( new InputStreamReader( stream ) );
        while ( ( line = reader.readLine() ) != null ) {
            result.append(line);

            if ( isErrorStream ) {
                System.err.println( line );
            } else {
                System.out.println( line );
            }
        }
        return result.toString() + System.lineSeparator();
    }

}
