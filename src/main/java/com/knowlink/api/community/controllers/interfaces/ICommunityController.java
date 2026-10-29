package com.knowlink.api.community.controllers.interfaces;

import com.knowlink.api.community.controllers.requests.CreatePostRequest;
import com.knowlink.api.community.controllers.requests.CreateReplyRequest;
import com.knowlink.api.community.controllers.requests.UpdatePostRequest;
import com.knowlink.api.community.controllers.requests.UpdateReplyRequest;
import com.knowlink.api.community.controllers.responses.CommunityPostDetailResponse;
import com.knowlink.api.community.controllers.responses.CommunityPostResponse;
import com.knowlink.api.community.controllers.responses.CommunityReplyResponse;
import com.knowlink.api.community.controllers.responses.CommunitySubjectResponse;
import com.knowlink.api.community.controllers.responses.HelpfulMarkResponse;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.shared.responses.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/community")
@Tag(name = "Comunidad", description = "Feed de la comunidad académica: publicaciones, respuestas y marcas \"Esto me sirvió\"")
public interface ICommunityController {

    @GetMapping("/posts")
    @Operation(summary = "Ver el feed de la comunidad",
            description = "Publicaciones de la comunidad ordenadas de la más reciente a la más antigua, con tipo, materia, "
                    + "autor, estado, cantidad de respuestas y marcas \"Esto me sirvió\" de su mejor respuesta.")
    @ApiResponse(responseCode = "200", description = "Página de publicaciones")
    @ApiResponse(responseCode = "401", description = "Sin autenticar")
    @ResponseStatus(OK)
    PagedResponse<CommunityPostResponse> getFeed(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Página (desde 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máximo 50)") @RequestParam(defaultValue = "10") int size);

    @GetMapping("/posts/mine")
    @Operation(summary = "Ver mis publicaciones",
            description = "Solo las publicaciones creadas por el usuario autenticado. Con date se filtran las creadas ese día.")
    @ApiResponse(responseCode = "200", description = "Página de publicaciones propias (vacía si todavía no publicó ninguna)")
    @ApiResponse(responseCode = "401", description = "Sin autenticar")
    @ResponseStatus(OK)
    PagedResponse<CommunityPostResponse> getMyPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Día de publicación (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Página (desde 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máximo 50)") @RequestParam(defaultValue = "10") int size);

    @GetMapping("/posts/{postId}")
    @Operation(summary = "Ver el detalle de una publicación con todas sus respuestas",
            description = "La respuesta destacada (si la publicación está resuelta) viene primero; el resto, de la más antigua a la más reciente.")
    @ApiResponse(responseCode = "200", description = "Publicación con sus respuestas")
    @ApiResponse(responseCode = "404", description = "La publicación no existe o fue eliminada")
    @ResponseStatus(OK)
    CommunityPostDetailResponse getDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId);

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Crear una publicación (pedido de ayuda o contenido compartido)",
            description = "Disponible para alumnos y tutores. Un pedido de ayuda admite hasta 500 caracteres de descripción; "
                    + "un contenido compartido, hasta 2000. Admite un archivo adjunto opcional (PDF, JPG, JPEG, PNG o XLSX). "
                    + "Una vez publicada no puede editarse, solo eliminarse.")
    @ApiResponse(responseCode = "201", description = "Publicación creada y visible en el feed")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, descripción demasiado larga o adjunto inválido")
    @ApiResponse(responseCode = "401", description = "Sin autenticar")
    @ApiResponse(responseCode = "404", description = "La materia no existe")
    @ResponseStatus(CREATED)
    CommunityPostResponse createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("request") @Valid CreatePostRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file);

    @PutMapping(value = "/posts/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Editar una publicación propia",
            description = "Permite cambiar el título, la descripción y el adjunto mientras la publicación esté abierta: un archivo "
                    + "nuevo reemplaza al actual, y removeAttachment=true lo quita. El tipo y la materia no cambian. Un pedido de "
                    + "ayuda admite hasta 500 caracteres de descripción; un contenido compartido, 2000.")
    @ApiResponse(responseCode = "200", description = "Publicación actualizada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, descripción demasiado larga o adjunto inválido")
    @ApiResponse(responseCode = "403", description = "La publicación no es del usuario")
    @ApiResponse(responseCode = "404", description = "La publicación no existe o fue eliminada")
    @ApiResponse(responseCode = "422", description = "La publicación ya está resuelta")
    @ResponseStatus(OK)
    CommunityPostResponse updatePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @RequestPart("request") @Valid UpdatePostRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file);

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "Eliminar una publicación propia")
    @ApiResponse(responseCode = "204", description = "Publicación eliminada")
    @ApiResponse(responseCode = "403", description = "La publicación no es del usuario")
    @ApiResponse(responseCode = "404", description = "La publicación no existe o ya fue eliminada")
    @ResponseStatus(NO_CONTENT)
    void deletePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId);

    @PostMapping("/posts/{postId}/resolve")
    @Operation(summary = "Marcar un pedido de ayuda propio como resuelto",
            description = "Destaca la respuesta con más marcas \"Esto me sirvió\" y deja de aceptar nuevas respuestas.")
    @ApiResponse(responseCode = "200", description = "Publicación resuelta")
    @ApiResponse(responseCode = "403", description = "La publicación no es del usuario")
    @ApiResponse(responseCode = "404", description = "La publicación no existe o fue eliminada")
    @ApiResponse(responseCode = "422", description = "No es un pedido de ayuda o ya estaba resuelto")
    @ResponseStatus(OK)
    CommunityPostResponse resolvePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId);

    @PostMapping(value = "/posts/{postId}/replies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Responder una publicación",
            description = "Disponible para alumnos y tutores. Un tutor puede incluir una tarjeta de vinculación a tutoría. "
                    + "El autor de la publicación solo puede responder cuando otra persona ya respondió. "
                    + "Notifica al autor de la publicación.")
    @ApiResponse(responseCode = "201", description = "Respuesta creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o adjunto inválido")
    @ApiResponse(responseCode = "404", description = "La publicación no existe o fue eliminada")
    @ApiResponse(responseCode = "422", description = "La publicación ya está resuelta, o es del propio usuario y nadie más respondió")
    @ResponseStatus(CREATED)
    CommunityReplyResponse createReply(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @RequestPart("request") @Valid CreateReplyRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file);

    @PutMapping(value = "/posts/{postId}/replies/{replyId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Editar una respuesta propia",
            description = "Permite cambiar el texto y el adjunto: un archivo nuevo reemplaza al actual, y removeAttachment=true "
                    + "lo quita. Sin ninguno de los dos, el adjunto se conserva.")
    @ApiResponse(responseCode = "200", description = "Respuesta actualizada")
    @ApiResponse(responseCode = "400", description = "Texto vacío o adjunto inválido")
    @ApiResponse(responseCode = "403", description = "La respuesta no es del usuario")
    @ApiResponse(responseCode = "404", description = "La publicación o la respuesta no existen")
    @ResponseStatus(OK)
    CommunityReplyResponse updateReply(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @PathVariable UUID replyId,
            @RequestPart("request") @Valid UpdateReplyRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file);

    @DeleteMapping("/posts/{postId}/replies/{replyId}")
    @Operation(summary = "Eliminar una respuesta propia")
    @ApiResponse(responseCode = "204", description = "Respuesta eliminada")
    @ApiResponse(responseCode = "403", description = "La respuesta no es del usuario")
    @ApiResponse(responseCode = "404", description = "La publicación o la respuesta no existen")
    @ResponseStatus(NO_CONTENT)
    void deleteReply(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @PathVariable UUID replyId);

    @PutMapping("/posts/{postId}/replies/{replyId}/helpful")
    @Operation(summary = "Marcar una respuesta como \"Esto me sirvió\"",
            description = "Idempotente. Un usuario no puede marcar su propia respuesta.")
    @ApiResponse(responseCode = "200", description = "Marca registrada con el total vigente")
    @ApiResponse(responseCode = "404", description = "La publicación o la respuesta no existen")
    @ApiResponse(responseCode = "422", description = "El usuario intentó marcar su propia respuesta")
    @ResponseStatus(OK)
    HelpfulMarkResponse markHelpful(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @PathVariable UUID replyId);

    @DeleteMapping("/posts/{postId}/replies/{replyId}/helpful")
    @Operation(summary = "Quitar la marca \"Esto me sirvió\" de una respuesta", description = "Idempotente.")
    @ApiResponse(responseCode = "200", description = "Marca retirada con el total vigente")
    @ApiResponse(responseCode = "404", description = "La publicación o la respuesta no existen")
    @ResponseStatus(OK)
    HelpfulMarkResponse unmarkHelpful(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @PathVariable UUID replyId);

    @GetMapping("/subjects")
    @Operation(summary = "Listar las materias del catálogo disponibles para publicar")
    @ApiResponse(responseCode = "200", description = "Materias ordenadas por nombre")
    @ResponseStatus(OK)
    List<CommunitySubjectResponse> getSubjects();
}
