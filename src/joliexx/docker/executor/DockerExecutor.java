package joliexx.docker.executor;

import com.sun.istack.internal.NotNull;
import jolie.runtime.FaultException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Executing docker commands as command line interface
 * Currently only on unix machines with user in docker group
 */
public class DockerExecutor {

    private static final String DOCKER_INVOCATION = "docker";

    public final class RunResults {
        private final String stdout;
        private final String stderr;
        private final Integer exitCode;

        public RunResults(String stdout, String stderr, Integer exitCode) {
            this.stderr = stderr;
            this.stdout = stdout;
            this.exitCode = exitCode;
        }

        public Integer getExitCode() {
            return exitCode;
        }

        public String getStderr() {
            return stderr;
        }

        public String getStdout() {
            return stdout;
        }
    }

    public RunResults executeDocker(String... args) throws FaultException {

        try {

            String[] execArgs = new String[args.length + 1];

            System.arraycopy(args, 0, execArgs, 1, args.length);
            execArgs[0] = DOCKER_INVOCATION;

            Process process = new ProcessBuilder(
                    execArgs
            ).start();

            try {

                return new RunResults( readStream(process.getInputStream(), false),
                        readStream(process.getErrorStream(), true), process.waitFor() );

            } catch (InterruptedException interruptedException) {

                throw new FaultException(interruptedException);
            }
        } catch (IOException ioException) {

            throw new FaultException(ioException);
        }
    }

    private static String readStream(@NotNull InputStream stream, Boolean isErrorStream ) throws IOException {
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
