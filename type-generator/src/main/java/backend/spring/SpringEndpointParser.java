package backend.spring;

import annotations.queries.PageParam;
import annotations.queries.PagedQuery;
import backend.EndPointParser;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import lombok.SneakyThrows;
import model.PagedEndpoint;
import model.TypeContext;
import model.Endpoint;
import backend.TypeParser;
import model.types.*;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class SpringEndpointParser implements EndPointParser {
    private final TypeParser typeParser;

    private final TypeContext context;

    private static final Set<String> IGONRED_ENDPOINT_PARAMS = Set.of(
            "org.springframework.web.server.ServerWebExchange",
            "org.springframework.web.server.WebSession",
            "jakarta.servlet.http.HttpServletRequest",
            "jakarta.servlet.http.HttpServletResponse"
    );

    public SpringEndpointParser(TypeContext context) {
        this.typeParser = new SpringTypeParser(context);
        this.context = context;
    }

    @Override
    @SneakyThrows
    public List<Endpoint> parseClass(CtClass clazz) {
        RestController annotation = (RestController) clazz.getAnnotation(RestController.class);
        RequestMapping requestMapping = (RequestMapping) clazz.getAnnotation(RequestMapping.class);
        if(annotation == null)  {
            return new ArrayList<>();
        }
        List<Endpoint> list = new ArrayList<>();
        if(requestMapping != null && requestMapping.value().length > 0) {
            for(String prefix: requestMapping.value()) {
                for (CtMethod method : clazz.getMethods()) {
                    parseMethod(method, list, prefix);
                }
            }
        } else {
            for (CtMethod method : clazz.getMethods()) {
                parseMethod(method, list, "");
            }
        }

        return list;
    }

    @SneakyThrows
    public void parseMethod(CtMethod method, List<Endpoint> endpoints, String prefix) {
        GetMapping getMapping = (GetMapping) method.getAnnotation(GetMapping.class);
        PostMapping postMapping = (PostMapping) method.getAnnotation(PostMapping.class);
        PutMapping putMapping = (PutMapping) method.getAnnotation(PutMapping.class);
        PatchMapping patchMapping = (PatchMapping) method.getAnnotation(PatchMapping.class);
        DeleteMapping deleteMapping = (DeleteMapping) method.getAnnotation(DeleteMapping.class);

        String className = method.getDeclaringClass().getSimpleName();

        if(getMapping != null) {
            for (String path : getMapping.value()) {
                endpoints.add(endpoint(method, prefix, HttpMethod.GET, className, path));
            }
            if (getMapping.value().length == 0) {
                endpoints.add(endpoint(method, prefix, HttpMethod.GET, className, null));
            }
        }

        if(postMapping != null) {
            for (String path : postMapping.value()) {
                endpoints.add(endpoint(method, prefix, HttpMethod.POST, className, path));
            }
            if (postMapping.value().length == 0) {
                endpoints.add(endpoint(method, prefix, HttpMethod.POST, className, null));
            }
        }

        if(putMapping != null) {
            for (String path : putMapping.value()) {
                endpoints.add(endpoint(method, prefix, HttpMethod.PUT, className, path));
            }
            if (putMapping.value().length == 0) {
                endpoints.add(endpoint(method, prefix, HttpMethod.PUT, className, null));
            }
        }

        if(patchMapping != null) {
            for (String path : patchMapping.value()) {
                endpoints.add(endpoint(method, prefix, HttpMethod.PATCH, className, path));
            }
            if (patchMapping.value().length == 0) {
                endpoints.add(endpoint(method, prefix, HttpMethod.PATCH, className, null));
            }
        }

        if(deleteMapping != null) {
            for (String path : deleteMapping.value()) {
                endpoints.add(endpoint(method, prefix, HttpMethod.DELETE, className, path));
            }
            if (deleteMapping.value().length == 0) {
                endpoints.add(endpoint(method, prefix, HttpMethod.DELETE, className, null));
            }
        }
    }

    @SneakyThrows
    public void parseArgs(CtMethod method, Endpoint endpoint) {
        String signature = method.getGenericSignature() == null ? method.getSignature() : method.getGenericSignature();
        String args = signature.replaceFirst("\\).*$", ")").replace(")", "").replace("(", "");
        String returnTypeStr = signature.replaceFirst("\\(.*\\)", "");
        GenericTypeStringParser argsParser = new GenericTypeStringParser(args, context);
        GenericTypeStringParser returnParser = new GenericTypeStringParser(returnTypeStr, context);
        List<Intermediate> argTypes = argsParser.parseGenericArgs();
        Intermediate returnType = returnParser.parseGenericArgs().get(0);
        endpoint.setReturnType(typeParser.parseType(returnType));

        MethodInfo methodInfo = method.getMethodInfo();
        LocalVariableAttribute table = (LocalVariableAttribute) methodInfo.getCodeAttribute().getAttribute(javassist.bytecode.LocalVariableAttribute.tag);

        for (int i = 0; i < method.getParameterAnnotations().length; i++) {
            int nameIndex = table.nameIndex(i + 1);
            String variableName = methodInfo.getConstPool().getUtf8Info(nameIndex);

            if(method.getParameterAnnotations()[i].length == 0 && !IGONRED_ENDPOINT_PARAMS.contains(method.getParameterTypes()[i].getName())){
                endpoint.getParams().add(new Field(variableName, typeParser.parseType(argTypes.get(i))));
            }

            for(Object annotation: method.getParameterAnnotations()[i]) {
                if(annotation instanceof RequestParam requestParam) {
                    Field field = new Field(variableName, typeParser.parseType(argTypes.get(i)));
                    field.setRequired(requestParam.required());
                    endpoint.getParams().add(field);

                    if(Arrays.stream(method.getParameterAnnotations()[i]).anyMatch(a -> a instanceof PageParam)) {
                        if(endpoint instanceof PagedEndpoint pe) {
                            if(pe.getPageVariable() == null) {
                                pe.setPageVariable(field);
                            } else {
                                System.err.println("Multiple page variables defined in endpoint " + endpoint.getClassName() + "." + endpoint.getName());
                            }
                        } else {
                            System.err.println("Unused @PageParam annotation encountered in " + endpoint.getClassName() + "." + endpoint.getName());
                        }
                    }
                } else if(annotation instanceof PathVariable) {
                    endpoint.getUrlArgs().add(new Field(variableName, typeParser.parseType(argTypes.get(i))));
                } else if(annotation instanceof RequestBody) {
                    endpoint.setBody(typeParser.parseType(argTypes.get(i)));
                    setNeedsValidation(endpoint.getBody());
                }
            }
        }
        if(endpoint instanceof PagedEndpoint pe && pe.getPageVariable() == null) {
            throw new RuntimeException("Encountered Paged endpoint without a page variable for endpoint " + endpoint.getClassName() + "." + endpoint.getName());
        }
    }

    private void setNeedsValidation(Type type) {

        if(type instanceof ObjectType o) {
            o.setNeedsValidation(true);
            o.getFields().forEach(field -> setNeedsValidation(field.getType()));
        }

        if(type instanceof EnumType e) {
            e.setNeedsValidation(true);
        }

        if(type instanceof ArrayType arr) {
            setNeedsValidation(arr.getSubType());
        }

        if(type instanceof MapType map) {
            setNeedsValidation(map.getKeySubType());
            setNeedsValidation(map.getValueSubType());
        }
    }

    @SneakyThrows
    private Endpoint endpoint(CtMethod method, String prefix, HttpMethod httpMethod, String className, String path) {
        String url = prefix + (path != null ? path : "");
        Endpoint endpoint;
        if(method.getAnnotation(PagedQuery.class) != null) {
            if(httpMethod.equals(HttpMethod.GET)) {
                endpoint = new PagedEndpoint(className, method.getName(), url, httpMethod, typeParser.parseType(method));
            } else {
                System.err.println("Only GET Methods may be paged in " + className + "." + method.getName());
                endpoint = new Endpoint(className, method.getName(), url, httpMethod, typeParser.parseType(method));
            }
        } else {
            endpoint = new Endpoint(className, method.getName(), url, httpMethod, typeParser.parseType(method));
        }

        parseArgs(method, endpoint);
        return endpoint;
    }
}

