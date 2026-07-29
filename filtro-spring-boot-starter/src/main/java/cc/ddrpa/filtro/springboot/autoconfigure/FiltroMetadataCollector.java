package cc.ddrpa.filtro.springboot.autoconfigure;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.annotation.Filtro;
import cc.ddrpa.filtro.core.annotation.FiltroQuery;
import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroFieldMetaBuilder;
import cc.ddrpa.filtro.core.provider.AnnotatedClassFiltroFieldMetaProvider;
import cc.ddrpa.filtro.springboot.FiltroFieldMetaVO;
import cc.ddrpa.filtro.springboot.properties.FiltroProperties;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class FiltroMetadataCollector implements SmartInitializingSingleton {
    private static final Logger logger = LoggerFactory.getLogger(FiltroMetadataCollector.class);

    private final FiltroProperties filtroProperties;
    private final FiltroRegistry filtroRegistry;
    private final AnnotatedClassFiltroFieldMetaProvider annotatedProvider;
    private final RequestMappingInfoHandlerMapping requestMappingInfoHandlerMapping;

    // metadata endpoint path -> criteriaType,metadataGroup
    private final ConcurrentMap<String, Pair<Class<?>, Class<?>>> metadataEndpoint2TypeAndGroup = new ConcurrentHashMap<>();

    public FiltroMetadataCollector(FiltroProperties properties,
                                   FiltroRegistry registry,
                                   AnnotatedClassFiltroFieldMetaProvider annotatedProvider,
                                   RequestMappingInfoHandlerMapping handlerMapping) {
        this.filtroProperties = properties;
        this.filtroRegistry = registry;
        this.annotatedProvider = annotatedProvider;
        this.requestMappingInfoHandlerMapping = handlerMapping;
    }

    @Override
    public void afterSingletonsInstantiated() {
        String[] packages = filtroProperties.getControllerPackages();
        if (packages.length == 0) {
            logger.info("No controller packages configured for FiltroQuery scanning; skipping metadata collection");
            return;
        }
        logger.info("FiltroQuery scanning controllers in packages: {}", (Object) packages);
        // 避免 @RestController（元标注了 @Controller）被处理两次
        Set<String> processedClasses = new HashSet<>();
        try (ScanResult scanResult = new ClassGraph()
                .enableAnnotationInfo()
                .acceptPackages(packages)
                .scan()) {
            for (Class<?> controllerClazz : scanResult.getClassesWithAnnotation(RestController.class.getName()).loadClasses()) {
                processControllerClass(controllerClazz);
                processedClasses.add(controllerClazz.getName());
            }
            for (Class<?> controllerClazz : scanResult.getClassesWithAnnotation(Controller.class.getName()).loadClasses()) {
                if (processedClasses.add(controllerClazz.getName())) {
                    processControllerClass(controllerClazz);
                }
            }
        }
        logger.info("FiltroQuery scanning complete — {} entity types registered, {} metadata endpoints",
                annotatedProvider.registeredTypeCount(), metadataEndpoint2TypeAndGroup.size());
    }

    /**
     * @return 已注册的元数据端点 path → (criteriaType, group)，只读视图
     */
    public Map<String, Pair<Class<?>, Class<?>>> getRegisteredMetadataEndpoints() {
        return Collections.unmodifiableMap(metadataEndpoint2TypeAndGroup);
    }

    /**
     * FiltroQuery 元信息 endpoint 处理方法
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
        Set<String> patterns;
        if (originalMappingInfo.getPathPatternsCondition() != null) {
            patterns = originalMappingInfo.getPathPatternsCondition()
                    .getPatterns()
                    .stream()
                    .map(PathPattern::getPatternString)
                    .collect(Collectors.toSet());
        } else {
            patterns = originalMappingInfo.getDirectPaths();
        }
        String originalPattern = patterns.isEmpty() ? ("/" + targetMethod.getName()) : patterns.iterator().next();

        // 构造 metadata endpoint 路径
        String expectedMatadataEndpointPath = originalPattern + filtroProperties.getMetadataEndpointSuffix();
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
        boolean metadataEndpointRegistered = false;
        for (Parameter parameter : method.getParameters()) {
            if (!parameter.isAnnotationPresent(FiltroQuery.class)) {
                continue;
            }
            FiltroQuery filtroQueryAnno = parameter.getAnnotation(FiltroQuery.class);
            Class<?> criteriaType = filtroQueryAnno.value();
            Class<?> metadataGroup = filtroQueryAnno.group();
            // 元数据端点每个方法只注册一次
            if (!metadataEndpointRegistered && filtroProperties.isEnableMetadataEndpoint()) {
                try {
                    Optional<String> metadataEndpoint = registerMetadataEndpoint(method);
                    metadataEndpoint.ifPresent(s ->
                            metadataEndpoint2TypeAndGroup.put(s, ImmutablePair.of(criteriaType, metadataGroup)));
                    metadataEndpointRegistered = true;
                } catch (NoSuchMethodException e) {
                    logger.warn("Failed to register FiltroQuery metadata endpoint for method {}: {}",
                            method.getName(), e.getMessage());
                }
            }
            if (annotatedProvider.hasType(criteriaType)) {
                logger.debug("FiltroQuery entity '{}' already registered, skipping", criteriaType.getSimpleName());
                continue;
            }
            List<FiltroFieldMeta> filtroFieldMetas = new ArrayList<>();
            for (Field field : criteriaType.getDeclaredFields()) {
                Filtro fieldSpecAnno = field.getAnnotation(Filtro.class);
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
            annotatedProvider.register(criteriaType, filtroFieldMetas);
            logger.info("FiltroQuery registered entity '{}' with {} filterable fields from controller {}",
                    criteriaType.getSimpleName(), filtroFieldMetas.size(),
                    method.getDeclaringClass().getSimpleName());
        }
    }
}
