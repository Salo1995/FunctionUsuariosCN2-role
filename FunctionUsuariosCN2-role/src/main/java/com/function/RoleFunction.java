package com.function;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.function.model.Rol;
import com.function.service.RoleService;
import com.function.exception.RolNotFoundException;
import com.function.exception.ValidationException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import java.util.Optional;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.HashMap;

public class RoleFunction {
    private final RoleService roleService = new RoleService();
    private static final Gson gson = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>) 
            (src, typeOfSrc, context) -> context.serialize(src.toString()))
        .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>) 
            (json, typeOfT, context) -> LocalDateTime.parse(json.getAsString()))
        .create();

    @FunctionName("roles")
    public HttpResponseMessage handleRoles(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT},
            authLevel = AuthorizationLevel.ANONYMOUS
        ) HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        context.getLogger().info("Procesando solicitud para roles");

        String method = request.getHttpMethod().name();

        try {
            switch (method) {
                case "GET":
                    return handleGetRoles(request);
                case "POST":
                    return handlePostRole(request);
                case "PUT":
                    return handlePutRole(request);
                default:
                    return request.createResponseBuilder(HttpStatus.METHOD_NOT_ALLOWED)
                        .body("Método HTTP no soportado")
                        .build();
            }
        } catch (ValidationException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Error de validación");
            response.put("detalles", e.getViolations());
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body(response)
                .build();
        } catch (RolNotFoundException e) {
            return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                .body(e.getMessage())
                .build();
        } catch (Exception e) {
            context.getLogger().severe("Error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error interno del servidor: " + e.getMessage())
                .build();
        }
    }

    private HttpResponseMessage handleGetRoles(HttpRequestMessage<Optional<String>> request) {
        String id = request.getQueryParameters().get("id");
        if (id != null) {
            try {
                Rol rol = roleService.getRoleById(Long.parseLong(id)); 
                if (rol == null) {
                    throw new RolNotFoundException("Rol no encontrado");
                }
                return request.createResponseBuilder(HttpStatus.OK)
                    .body(gson.toJson(rol))
                    .build();
            } catch (NumberFormatException e) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("ID inválido")
                    .build();
            }
        }
        return request.createResponseBuilder(HttpStatus.OK)
            .body(gson.toJson(roleService.getAllRoles())) 
            .build();
    }

    private HttpResponseMessage handlePostRole(HttpRequestMessage<Optional<String>> request) {
        Optional<String> requestBody = request.getBody();
        
        if (!requestBody.isPresent()) { 
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("El cuerpo de la solicitud no puede estar vacío")
                .build();
        }
    
        try {
            Rol nuevoRol = gson.fromJson(requestBody.get(), Rol.class);
            Rol creado = roleService.createRole(nuevoRol);
    
            return request.createResponseBuilder(HttpStatus.CREATED)
                .body(gson.toJson(creado))
                .build();
        } catch (JsonSyntaxException e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Error en el formato del JSON")
                .build();
        }
    }

    private HttpResponseMessage handlePutRole(HttpRequestMessage<Optional<String>> request) {
        Optional<String> requestBody = request.getBody();
        if (!requestBody.isPresent()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("El cuerpo de la solicitud no puede estar vacío")
                .build();
        }
        
        try {
            Rol rolActualizado = gson.fromJson(requestBody.get(), Rol.class);
            Rol updated = roleService.updateRole(rolActualizado);
        
            return request.createResponseBuilder(HttpStatus.OK)
                .body(gson.toJson(updated))
                .build();
        } catch (JsonSyntaxException e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Error en el formato del JSON")
                .build();
        } catch (IllegalArgumentException e) {
            return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                .body("Rol no encontrado")
                .build();
        }
    }
}
