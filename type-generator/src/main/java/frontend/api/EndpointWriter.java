package frontend.api;

import frontend.TypeScriptFile;
import model.Endpoint;

import java.util.List;

public interface EndpointWriter {
    String ENDPOINTS_DIR = "endpoints";

    List<TypeScriptFile> printAllEndPoints(List<Endpoint> endpoints);
}
