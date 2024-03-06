package frontend.api.angular;

import frontend.TypeScriptFile;
import frontend.api.EndpointWriter;
import frontend.types.TypeWriter;
import lombok.RequiredArgsConstructor;
import model.Endpoint;
import model.TypeContext;
import model.types.ArrayType;
import model.types.Field;
import model.types.PrimitiveType;

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
            setUpFile(typeScriptFile, classEndpoints, className);

            StringBuilder body = new StringBuilder();
            body.append(buildHeader(className));

            classEndpoints.forEach(endpoint -> body.append(buildEndpoint(typeScriptFile, endpoint)));
            body.append("}\n");

            typeScriptFile.setBody(body.toString());
            files.add(typeScriptFile);
        });

        return files;
    }

    private void setUpFile(TypeScriptFile typeScriptFile, List<Endpoint> classEndpoints, String className) {
        typeScriptFile.getImports().addAll(defaultImports);
        if (classEndpoints.stream().filter(endpoint -> !endpoint.getParams().isEmpty()).toList().isEmpty()) {
            typeScriptFile.getImports().add(new TypeScriptFile.Import("@angular/common/http", null, Set.of("HttpClient")));
        } else {
            typeScriptFile.getImports().add(new TypeScriptFile.Import("@angular/common/http", null, Set.of("HttpClient", "HttpParams")));
        }

        String fileName = (className.replace("Controller", ".service"));
        for (int i = 0; i < fileName.length(); i++) {
            if (i != 0 && Character.isUpperCase(fileName.charAt(i))) {
                fileName = fileName.substring(0, i) + "-" + Character.toLowerCase(fileName.charAt(i)) + fileName.substring(i+1);
            }
        }
        typeScriptFile.setLocation(basePath + ENDPOINTS_DIR + "/" + fileName.toLowerCase());
    }

    private StringBuilder buildHeader(String className) {
        StringBuilder header = new StringBuilder();
        header.append("const headers = { 'content-type': 'application/json' };\n\n").append("@Injectable({\n    providedIn: 'root',\n})\nexport class ").append(className.replace("Controller", "Service")).append(" {\n").append("    baseURL = environment.serverUrl;\n\n");
        header.append("    constructor(private http: HttpClient) {}\n");
        return header;
    }

    private StringBuilder buildEndpoint(TypeScriptFile typeScriptFile, Endpoint endpoint) {
        StringBuilder endpointString = new StringBuilder();

        int paramCount = endpoint.getParams().size();
        String returnType = TypeWriter.printType(endpoint.getReturnType(), context);
        String urlBody = endpoint.getBody() != null ? "body" : "null";

        endpointString.append("\n    ").append(endpoint.getName()).append("(");
        endpointString.append(buildEndpointInputs(typeScriptFile, endpoint, paramCount));
        endpointString.append("): Observable<");
        endpointString.append(returnType);
        typeScriptFile.addImport(endpoint.getReturnType(), context);
        endpointString.append("> {\n");

        if (!endpoint.getParams().isEmpty()) {
            endpointString.append(buildParams(endpoint.getParams()));
        }
        String httpMethod = endpoint.getHttpMethod().toString().toLowerCase();
        endpointString.append("        return this.http.").append(httpMethod).append("<").append(returnType).append(">(this.baseURL + `").append(endpoint.getUrl().replace("{", "${")).append("`, ");
        if (!httpMethod.equals("get") && !httpMethod.equals("delete")) {
            endpointString.append(urlBody).append(", ");
        }
        endpointString.append("{ headers").append(paramCount > 0 ? ", params" : "").append(" });\n    }\n");

        return endpointString;
    }

    private StringBuilder buildEndpointInputs(TypeScriptFile typeScriptFile, Endpoint endpoint, int paramCount) {
        StringBuilder endpointInputs = new StringBuilder();

        int argsCount = endpoint.getUrlArgs().size();
        if (endpoint.getBody() != null) {
            String bodyType = TypeWriter.printType(endpoint.getBody(), context);
            endpointInputs.append("body").append(": ").append(bodyType);
            typeScriptFile.addImport(endpoint.getBody(), context);
            if (paramCount > 0 || argsCount > 0) {
                endpointInputs.append(", ");
            }
        }
        if (!endpoint.getUrlArgs().isEmpty()) {
            for (int i = 0; i < argsCount; i++) {
                Field arg = endpoint.getUrlArgs().get(i);
                endpointInputs.append(arg.getName()).append(arg.isRequired() ? ": " : "?: ").append(TypeWriter.printType(arg.getType(), context));
                typeScriptFile.addImport(arg.getType(), context);
                if (((argsCount - 1) != i) || ((argsCount - 1) == i && paramCount != 0)) {
                    endpointInputs.append(", ");
                }
            }
        }
        if (!endpoint.getParams().isEmpty()) {
            for (int i = 0; i < paramCount; i++) {
                Field param = endpoint.getParams().get(i);
                endpointInputs.append(param.getName()).append(param.isRequired() ? ": " : "?: ").append(TypeWriter.printType(param.getType(), context));
                typeScriptFile.addImport(param.getType(), context);
                if ((paramCount - 1) != i) {
                    endpointInputs.append(", ");
                }
            }
        }

        return endpointInputs;
    }

    private StringBuilder buildParams(List<Field> params) {
        StringBuilder paramString = new StringBuilder();

        paramString.append("        let params = new HttpParams();\n");
        for (Field param : params) {
            if (!param.getType().toString().contains("ObjectType")) {
                if (!param.isRequired()) {
                    paramString.append("        if (").append(param.getName()).append(") {\n    ");
                }
                if (param.getType() instanceof ArrayType arr) {
                    if(arr.getSubType().equals(PrimitiveType.Date) && !context.isUseStringAsDate()) {
                        paramString.append("        params = ").append(param.getName()).append(".reduce((p, item) => p.append('").append(param.getName()).append("', item.toString()), params);\n");
                    } else {
                        paramString.append("        params = ").append(param.getName()).append(".reduce((p, item) => p.append('").append(param.getName()).append("', item), params);\n");
                    }
                } else {
                    if(param.getType().equals(PrimitiveType.Date) && !context.isUseStringAsDate()) {
                        paramString.append("        params = params.append('").append(param.getName()).append("', ").append(param.getName()).append(".toString());\n");
                    } else {
                        paramString.append("        params = params.append('").append(param.getName()).append("', ").append(param.getName()).append(");\n");
                    }
                }
                if (!param.isRequired()) {
                    paramString.append("        }\n");
                }
            }
        }

        return paramString;
    }
}