package joliexx.exec;

import jolie.runtime.FaultException;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.embedding.RequestResponse;
import jolie.runtime.typing.TypeCastingException;


public class ExecExtras {

    @RequestResponse
    public Value timedExec( Value request ) throws FaultException {
        Value response = Value.create();

        ValueVector args;
        String program, timeUnit;
        Boolean waitFor, stdoutPrint;
        Long timedOut;

        if ( request.hasChildren( "timedOut" ) ) {
            timedOut = request.getFirstChild( "timedOut" ).longValue();
        }

        if ( request.hasChildren( "args" ) ) {
            args = request.getChildren( "args" );
        }

        waitFor = request.getFirstChild( "waitFor" ).boolValue();
        stdoutPrint = request.getFirstChild( "stdoutPrintOut" ).boolValue();

        try {
            program = request.strValueStrict();
        } catch ( TypeCastingException tce ) {
            throw new FaultException( tce );
        }


        return response;
    }

}
