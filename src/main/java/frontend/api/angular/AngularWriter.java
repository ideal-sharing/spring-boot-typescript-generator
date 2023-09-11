package frontend.api.angular;

import frontend.TypeScriptFile;
import frontend.api.EndpointWriter;
import frontend.types.TypeWriter;
import lombok.RequiredArgsConstructor;
import model.Endpoint;
import model.TypeContext;
import model.types.Field;
import java.util.*;

@RequiredArgsConstructor
public class AngularWriter implements EndpointWriter {

    private final TypeContext context;
    private final String basePath;

    private final List<TypeScriptFile.Import> defaultImports = List.of(
            new TypeScriptFile.Import("@angular/core", null, Set.of("Injectable")),
            new TypeScriptFile.Import("rxjs", null, Set.of("Observable")),
            // TODO remove in the future
            new TypeScriptFile.Import("../../../environments/environment", null, Set.of("environment"))
    );

    @Override
    public List<TypeScriptFile> printAllEndPoints(List<Endpoint> endpoints) {
        List<TypeScriptFile> files = new ArrayList<>();

        Map<String, List<Endpoint>> endpointMap = new HashMap<>();
        endpoints.forEach(endpoint ->
                endpointMap.computeIfAbsent(endpoint.getClassName(), k -> new ArrayList<>())
                        .add(endpoint)
        );

        endpointMap.forEach((className, classEndpoints) -> {
            TypeScriptFile typeScriptFile = new TypeScriptFile();
            typeScriptFile.getImports().addAll(defaultImports);
            if (classEndpoints.stream().filter(endpoint -> !endpoint.getParams().isEmpty()).toList().isEmpty()) {
                typeScriptFile.getImports().add(new TypeScriptFile.Import("@angular/common/http", null, Set.of("HttpClient")));
            } else {
                typeScriptFile.getImports().add(new TypeScriptFile.Import("@angular/common/http", null, Set.of("HttpClient", "HttpParams")));
            }
            typeScriptFile.setLocation(basePath + ENDPOINTS_DIR + "/" + (className.replace("Controller", ".service").toLowerCase()));

            StringBuilder body = new StringBuilder();
            body.append("const headers = { 'content-type': 'application/json' };\n\n").append("@Injectable({\n    providedIn: 'root',\n})\nexport class ").append(className.replace("Controller", "Service")).append(" {\n").append("    baseURL = environment.serverUrl;\n\n");
            body.append("    constructor(private http: HttpClient) {}\n");
            classEndpoints.forEach(endpoint -> {
                int paramCount = endpoint.getParams().size();
                int argsCount = endpoint.getUrlArgs().size();
                String returnType = TypeWriter.printType(endpoint.getReturnType(), context);
                String urlBody = "null";

                body.append("\n    ").append(endpoint.getName()).append("(");
                if (endpoint.getBody() != null) {
                    String bodyType = TypeWriter.printType(endpoint.getBody(), context);
                    urlBody = bodyType.substring(0, 1).toLowerCase() + bodyType.substring(1);
                    body.append(urlBody).append(": ").append(bodyType);
                    typeScriptFile.addImport(endpoint.getBody(), context);
                    if (paramCount > 0 || argsCount > 0) {
                        body.append(", ");
                    }
                }
                if (!endpoint.getUrlArgs().isEmpty()) {
                    for (int i = 0; i < argsCount; i++) {
                        Field arg = endpoint.getUrlArgs().get(i);
                        body.append(arg.getName()).append(arg.isRequired() ? ": " : "?: ").append(TypeWriter.printType(arg.getType(), context));
                        if (((argsCount - 1) != i) || ((argsCount - 1) == i && paramCount != 0)) {
                            body.append(", ");
                        }
                    }
                }
                if (!endpoint.getParams().isEmpty()) {
                    for (int i = 0; i < paramCount; i++) {
                        Field param = endpoint.getParams().get(i);
                        body.append(param.getName()).append(param.isRequired() ? ": " : "?: ").append(TypeWriter.printType(param.getType(), context));
                        typeScriptFile.addImport(param.getType(), context);
                        if ((paramCount - 1) != i) {
                            body.append(", ");
                        }
                    }
                }
                body.append("): Observable<");
                body.append(returnType);
                typeScriptFile.addImport(endpoint.getReturnType(), context);
                body.append("> {\n");
                if (!endpoint.getParams().isEmpty()) {
                    body.append("        let params = new HttpParams();\n");
                    for (Field param : endpoint.getParams()) {
                        if (!param.getType().toString().contains("ObjectType")) {
                            body.append("        params = params.append('").append(param.getName()).append("', ").append(param.getName()).append(");\n");
                        }
                    }
                }
                String httpMethod = endpoint.getHttpMethod().toString().toLowerCase();
                body.append("        return this.http.").append(httpMethod).append("<").append(returnType).append(">(this.baseURL + `").append(endpoint.getUrl().replace("{", "${")).append("`, ");
                if (!httpMethod.equals("get") && !httpMethod.equals("delete")) {
                    body.append(urlBody).append(", ");
                }
                body.append("{ headers").append(paramCount > 0 ? ", params" : "").append(" });\n    }\n");
            });
            body.append("}\n");

            typeScriptFile.setBody(body.toString());
            files.add(typeScriptFile);
        });

        return files;
    }
}
