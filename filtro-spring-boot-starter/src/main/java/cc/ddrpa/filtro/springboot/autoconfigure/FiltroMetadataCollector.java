package cc.ddrpa.filtro.springboot.autoconfigure;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.annotation.Filtro;
import cc.ddrpa.filtro.core.annotation.FiltroField;
import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroFieldMetaBuilder;
import cc.ddrpa.filtro.springboot.FiltroFieldMetaVO;
import cc.ddrpa.filtro.springboot.properties.FiltroProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.reflections.scanners.Scanners.TypesAnnotated;

public class FiltroMetadataCollector implements SmartInitializingSingleton {
    private final FiltroProperties filtroProperties;
    private final FiltroRegistry filtroRegistry;
    private final RequestMappingInfoHandlerMapping requestMappingInfoHandlerMapping;

    // metadata endpoint path -> criteriaType,metadataGroup
    private final ConcurrentMap<String, Pair<Class<?>, Class<?>>> metadataEndpoint2TypeAndGroup = new ConcurrentHashMap<>();

    public FiltroMetadataCollector(FiltroProperties properties,
                                   FiltroRegistry registry,
                                   RequestMappingInfoHandlerMapping handlerMapping) {
        this.filtroProperties = properties;
        this.filtroRegistry = registry;
        this.requestMappingInfoHandlerMapping = handlerMapping;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Reflections reflections = new Reflections(new ConfigurationBuilder().forPackages(filtroProperties.getControllerPackages()));
        // 只扫描带 @RestController 或 @Controller 注解的类
        Set<Class<?>> restControllerClassSet = reflections.get(TypesAnnotated.with(RestController.class).asClass());
        for (Class<?> controllerClazz : restControllerClassSet) {
            processControllerClass(controllerClazz);
        }
        Set<Class<?>> controllerClassSet = reflections.get(TypesAnnotated.with(Controller.class).asClass());
        for (Class<?> controllerClazz : controllerClassSet) {
            processControllerClass(controllerClazz);
        }
    }

    /**
     * Filtro 元信息 endpoint 处理方法
     *
     * @param request
     * @param ignoredPathVars
     * @return
     */
    public ResponseEntity<List<FiltroFieldMetaVO>> filtroMetadata(HttpServletRequest request,
                                                                  @PathVariable Map<String, String> ignoredPathVars) {
        String endpointPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Pair<Class<?>, Class<?>> typeAndGroup = metadataEndpoint2TypeAndGroup.get(endpointPattern);
        List<FiltroFieldMetaVO> filtroFieldMetas = filtroRegistry.get(typeAndGroup.getLeft(), typeAndGroup.getRight())
                .stream()
                .map(FiltroFieldMetaVO::from)
                .toList();
        return ResponseEntity.ok(filtroFieldMetas);
    }

    /**
     * 为目标请求方法注册一个元信息 endpoint
     *
     * @param targetMethod
     * @return
     * @throws NoSuchMethodException
     */
    private Optional<String> registerMetadataEndpoint(Method targetMethod) throws NoSuchMethodException {
        Optional<Map.Entry<RequestMappingInfo, HandlerMethod>> optionalEntry = requestMappingInfoHandlerMapping.getHandlerMethods()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().getMethod().equals(targetMethod)).findFirst();
        if (optionalEntry.isEmpty()) {
            return Optional.empty();
        }
        RequestMappingInfo originalMappingInfo = optionalEntry.get().getKey();

        // 取第一个 pattern（如果有多个 pattern，可以循环注册多个 meta path）
        Set<String> patterns = originalMappingInfo.getPathPatternsCondition()
                .getPatterns()
                .stream()
                .map(PathPattern::getPatternString)
                .collect(Collectors.toSet());
        String originalPattern = patterns.isEmpty() ? ("/" + targetMethod.getName()) : patterns.iterator().next();

        // 构造 metadata endpoint 路径
        String expectedMatadataEndpointPath = originalPattern + ":filtro";
        expectedMatadataEndpointPath = expectedMatadataEndpointPath.replaceAll("//+", "/");

        // 元信息 handler bean（我们用 MetadataController.meta 方法）
        Method handlerMethod = this.getClass().getMethod("filtroMetadata", HttpServletRequest.class, Map.class);

        // 构造 RequestMappingInfo
        RequestMappingInfo metadataMappingInfo = RequestMappingInfo
                .paths(expectedMatadataEndpointPath)
                .methods(RequestMethod.GET)
                .build();
        requestMappingInfoHandlerMapping.registerMapping(metadataMappingInfo, this, handlerMethod);
        return Optional.of(expectedMatadataEndpointPath);
    }

    /**
     * 处理 Controller 类
     *
     * @param controller
     */
    private void processControllerClass(Class<?> controller) {
        for (Method method : controller.getDeclaredMethods()) {
            // 方法必须是 GetMapping 或 RequestMapping
            boolean isSupported = false;
            if (method.isAnnotationPresent(GetMapping.class)) {
                isSupported = true;
            } else if (method.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                if (Arrays.asList(requestMapping.method()).contains(RequestMethod.GET)) {
                    isSupported = true;
                }
            }
            if (!isSupported) {
                continue;
            }
            processMethod(method);
        }
    }

    /**
     * 处理 Controller 方法
     *
     * @param method
     */
    private void processMethod(Method method) {
        for (Parameter parameter : method.getParameters()) {
            if (!parameter.isAnnotationPresent(Filtro.class)) {
                continue;
            }
            Filtro filtroAnno = parameter.getAnnotation(Filtro.class);
            Class<?> criteriaType = filtroAnno.value();
            Class<?> metadataGroup = filtroAnno.group();
            // 获取 url 映射，在 endpoint 后面补充添加 ':filtro' 后注册一个新 endpoint
            // 访问该地址返回构造 schema
            try {
                Optional<String> metadataEndpoint = registerMetadataEndpoint(method);
                // NEED_CHECK 记录 mappingName -> criteriaType,metadataGroup 的映射关系
                metadataEndpoint.ifPresent(s ->
                        metadataEndpoint2TypeAndGroup.put(s, ImmutablePair.of(criteriaType, metadataGroup)));
            } catch (NoSuchMethodException e) {
                // IGNORED
            }
            if (filtroRegistry.hasType(criteriaType)) {
                // 如果已经注册过该 criteriaType，则跳过
                continue;
            }
            // 解析声明的 criteriaType
            List<FiltroFieldMeta> filtroFieldMetas = new ArrayList<>();
            for (Field field : criteriaType.getDeclaredFields()) {
                FiltroField fieldSpecAnno = field.getAnnotation(FiltroField.class);
                if (Objects.isNull(fieldSpecAnno)) {
                    continue;
                }
                FiltroFieldMetaBuilder builder = new FiltroFieldMetaBuilder(field, fieldSpecAnno);
                if (fieldSpecAnno.operators().length > 0) {
                    builder.setClaimedOperators(Set.of(fieldSpecAnno.operators()));
                }
                FiltroFieldMeta filtroFieldMeta = builder.build();
                filtroFieldMetas.add(filtroFieldMeta);
            }
            filtroRegistry.register(criteriaType, filtroFieldMetas);
            // 注册完一个立即退出，无法处理多个 Filtro 注解（只能生成一个 metadata endpoint）
            return;
        }
    }
}
