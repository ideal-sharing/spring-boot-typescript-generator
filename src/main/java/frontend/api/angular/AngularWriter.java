package frontend.api.angular;

import frontend.TypeScriptFile;
import frontend.api.EndpointWriter;
import lombok.RequiredArgsConstructor;
import model.Endpoint;
import model.TypeContext;

import java.util.List;

@RequiredArgsConstructor
public class AngularWriter implements EndpointWriter {

    private final TypeContext context;
    private final String basePath;

    @Override
    public List<TypeScriptFile> printAllEndPoints(List<Endpoint> endpoints) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
