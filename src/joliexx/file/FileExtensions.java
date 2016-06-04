package joliexx.file;

import jolie.runtime.FaultException;
import jolie.runtime.JavaService;
import jolie.runtime.Value;
import jolie.runtime.embedding.RequestResponse;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileExtensions extends JavaService {

    @RequestResponse
    public Value copyFile( Value request ) throws FaultException {
        Value result = Value.create();

        String src = request.getFirstChild("sourceFile").strValue();
        String dest = request.getFirstChild("destinationFile").strValue();

        File srcFile = new File(src);

        if ( srcFile.isFile() ) {

            if ( !srcFile.exists() ) {
                throw new FaultException( new FileNotFoundException( "Source not found" ) );
            }

            FileOutputStream destinationStream;

            try {
                destinationStream = new FileOutputStream( new File( dest ) );
            } catch ( FileNotFoundException fnfe ) {
                throw new FaultException( fnfe );
            }

            try {
                Files.copy( srcFile.toPath(), destinationStream );

            } catch ( IOException ioe ) {
                throw new FaultException( ioe );
            }

        } else {
            throw new FaultException( new IOException( "source is not a file" ) );
        }

        return result;
    }
}
