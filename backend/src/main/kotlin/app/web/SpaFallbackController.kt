package app.web

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.types.files.StreamedFile
import io.swagger.v3.oas.annotations.Hidden

@Controller
@Hidden
class SpaFallbackController {
    @Get("/d/{path:.*}", produces = [MediaType.TEXT_HTML])
    fun downloadSpa(): HttpResponse<StreamedFile> {
        val stream = javaClass.classLoader.getResourceAsStream("public/index.html") ?: return HttpResponse.notFound()
        return HttpResponse.ok(StreamedFile(stream, MediaType.TEXT_HTML_TYPE))
    }
}

