package joliexx.io;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;

class IoHelpers {

    MimetypesFileTypeMap mimeTypesMap = null;

    private void configMimeTypes() {
        mimeTypesMap = new MimetypesFileTypeMap();
        mimeTypesMap.addMimeTypes("image/jpeg jpg jpeg");
        mimeTypesMap.addMimeTypes("image/png png");
        mimeTypesMap.addMimeTypes("image/tiff tiff");
        mimeTypesMap.addMimeTypes("image/svg+xml svg");
        mimeTypesMap.addMimeTypes("application/xml xml");
        mimeTypesMap.addMimeTypes("application/javascript js");
        mimeTypesMap.addMimeTypes("text/css css");
        mimeTypesMap.addMimeTypes("application/pdf pdf");
    }

    String getMimeType(File file) {
        if (mimeTypesMap == null) {
            configMimeTypes();
        }
        return mimeTypesMap.getContentType(file);
    }

    String getMimeType(String filename) {
        return getMimeType(new File(filename));
    }


}
