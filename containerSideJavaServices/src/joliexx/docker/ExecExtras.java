package joliexx.docker;

import jolie.runtime.FaultException;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.embedding.RequestResponse;
import jolie.runtime.typing.TypeCastingException;
import joliexx.executor.TimeOutExecutor;

public class ExecExtras {

    @RequestResponse
    public Value timeOutExec(Value request ) throws FaultException {
        Value response = Value.create();

        ValueVector args = null;
        String program;
        Boolean stdoutPrint;
        Long timedOut;

        if ( request.hasChildren( "args" ) ) {
            args = request.getChildren( "args" );
        }

        stdoutPrint = request.hasChildren( "printOut" ) && request.getFirstChild( "printOut" ).boolValue();

        try {
            program = request.strValueStrict();
            timedOut = request.getFirstChild( "timedOut" ).longValueStrict();
        } catch (TypeCastingException tce) {
            throw new FaultException(tce);
        }

        joliexx.executor.TimeOutExecutor timeOutExecutor = new TimeOutExecutor();
        timeOutExecutor.setTimeOut( timedOut );
        TimeOutExecutor.RunResults programResults;

        if ( args == null ) {
            programResults = timeOutExecutor.execute( program, stdoutPrint );
        } else {
            String[] programArguments = new String[args.size()];
            for (int i = 0; i < args.size(); i++) {
                try {
                    programArguments[i] = args.get(i).strValueStrict();
                } catch (TypeCastingException tce) {
                    throw new FaultException(tce);
                }
            }
            programResults = timeOutExecutor.execute(program, stdoutPrint, programArguments);
        }

        if ( programResults.hasStdout() ) {
            response.setFirstChild( "stdout", programResults.getStdout() );
        }

        if ( programResults.hasStderr() ) {
            response.setFirstChild( "stderr", programResults.getStderr() );
        }

        response.setFirstChild( "exitCode", programResults.getExitCode() );

        return response;
    }

}
