package joliexx.file;

import jolie.runtime.FaultException;
import jolie.runtime.JavaService;
import jolie.runtime.Value;
import jolie.runtime.embedding.RequestResponse;

import java.io.*;
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

            Path destPath;
            try {
                destPath = Paths.get(dest).toAbsolutePath().normalize(); // check whether dest path is valid
            } catch ( InvalidPathException invalidPath ) {
                throw new FaultException( invalidPath );
            }

            BufferedReader fileReader;

            try {
                fileReader = new BufferedReader( new FileReader( srcFile ) );
            } catch ( FileNotFoundException fnfe ) {
                throw new FaultException( fnfe );
            }

            BufferedWriter fileWriter;

            try {
                fileWriter = new BufferedWriter( new FileWriter( destPath.toString() ) );
            } catch ( IOException ioe ) {
                throw new FaultException(ioe);
            }

            try {
                String lineIn;
                while ( ( lineIn = fileReader.readLine() ) != null ) {
                    fileWriter.write( lineIn + System.lineSeparator() );
                }

                fileWriter.flush();

                fileReader.close();
                fileWriter.close();

            } catch ( IOException ioe ) {
                throw new FaultException( ioe );
            }

        } else {
            throw new FaultException( new IOException( "source is not a file" ) );
        }

        return result;
    }
}
