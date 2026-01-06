package com.bot.elara.Infrastructure.External.Clients.ElaraApi;

import com.bot.elara.Infrastructure.External.Clients.ElaraApi.config.FeignConfig;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.RegisterConsultRequest;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.UploadPhotosRequest;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.VerifyPatientRequest;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response.RegisterConsultResponse;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response.UploadPhotosResponse;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response.VerifyPatientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign Client para operaciones de pacientes en la API externa de Elara
 * 
 * IMPORTANTE: Este cliente usa FeignConfig que automáticamente agrega:
 * - Content-Type: application/json
 * - Authorization: Bearer <token> (del login del sistema)
 * 
 * Endpoints:
 * - POST /api/patient/verify - Verificar si existe un paciente
 * - POST /api/consult/registry - Registrar o actualizar consulta completa
 * - POST /api/consult/{consult_id}/photos - Subir fotos de la consulta
 */
@FeignClient(
        name = "elara-patient-client",
        url = "${elara.api.base-url}",
        configuration = FeignConfig.class
)
public interface ElaraApiPatientClient {

    /**
     * POST /api/patient/verify/
     * 
     * Verifica si un paciente existe en el sistema basándose en su WhatsApp ID y email
     * 
     * Headers automáticos:
     * - Content-Type: application/json (agregado por Feign)
     * - Authorization: Bearer <token> (agregado por FeignAuthInterceptor)
     * 
     * Request JSON:
     * {
     *   "whatsappId": "521234567890",
     *   "email": "prueba@dominio.com"
     * }
     * 
     * Response JSON (ejemplo si existe):
     * {
     *   "exists": true,
     *   "patientId": "abc123xyz",
     *   "message": "Paciente encontrado"
     * }
     * 
     * Response JSON (ejemplo si NO existe):
     * {
     *   "exists": false,
     *   "patientId": null,
     *   "message": "Paciente no encontrado"
     * }
     * 
     * @param request Datos del paciente a verificar (whatsappId y email)
     * @return Información de verificación del paciente
     */
    @PostMapping("/api/patient/verify/")
    VerifyPatientResponse verifyPatient(@RequestBody VerifyPatientRequest request);

    /**
     * POST /api/consult/registry
     * 
     * Registra o actualiza una consulta completa del paciente con todos los datos
     * recopilados durante el flujo del bot de WhatsApp
     * 
     * Headers automáticos:
     * - Content-Type: application/json (agregado por Feign)
     * - Authorization: Bearer <token> (agregado por FeignAuthInterceptor)
     * 
     * Request JSON (todos los campos del paciente):
     * {
     *   "whatsappId": "5215545830244",
     *   "consultaId": "550e8400-e29b-41d4-a716-446655440000",  // Opcional para actualizaciones
     *   "padecimiento": "Acne",
     *   "email": "prueba@dominio.com",
     *   "Consulta": 1,  // 1=para mi, 2=para otra persona, 3=menor de 18
     *   "nombreCompleto": "Miguel Angel Otañez",
     *   "genero": "Masculino",
     *   "fechaNacimiento": "14-04-1995",
     *   "pesoKg": 72.5,
     *   "alturaM": 1.75,
     *   "fuma": false,
     *   "desdeCuando": "Hace meses",
     *   "tratamientoAnterior": true,
     *   "tratamientosUsados": "Crema X, Roacutan",
     *   "alergias": true,
     *   "alergiasDetalles": "Penicilina",
     *   "medicamentos": false,
     *   "medicamentosDetalles": "",
     *   "enfermedades": true,
     *   "enfermedadesDetalles": "Asma",
     *   "notadermatologo": "Tambien me sucede ...",
     *   
     *   // Campos condicionales según padecimiento:
     *   "areaCaida": "Entradas",
     *   "antecedentesFamiliares": "Padre y abuelo",
     *   "gravedadAcne": "Moderado",
     *   "gravedadManchas": "Leve",
     *   "gravedadRosacea": "Grave",
     *   "mejoraPrincipal": "Arrugas",
     *   "tipoPiel": "Mixta",
     *   "sensibilidadPiel": "A veces",
     *   "exposicionSol": "Diario",
     *   "usaProtector": "Sí siempre",
     *   "statusEmbarazo": "Ninguna",
     *   
     *   "photoUrls": ["https://s3.amazonaws.com/fotos/1.jpg", "..."],
     *   "notasAdicionales": "Le salen más después del gym",
     *   
     *   "aceptaTerminos": true,
     *   "aceptaAvisoPrivacidad": true,
     *   "aceptaConsentimiento": true,
     *   
     *   "pagoProcesado": true,
     *   "pagoId": "pi_3Q1a2b3c...",
     *   
     *   "canalOrigen": "WHATSAPP",
     *   "createdAt": "2025-12-03T12:00:00Z",
     *   "updatedAt": "2025-12-03T12:05:00Z"
     * }
     * 
     * Response JSON (éxito):
     * {
     *   "code": 200,
     *   "status": "ok",
     *   "mensaje": "Consulta registrada/actualizada correctamente",
     *   "consultaId": "550e8400-e29b-41d4-a716-446655440000"
     * }
     * 
     * @param request Datos completos de la consulta del paciente
     * @return Respuesta con el consultaId generado o actualizado
     */
    @PostMapping("/api/consult/registry/")
    RegisterConsultResponse registerConsult(@RequestBody RegisterConsultRequest request);

    /**
     * POST /api/consult/{consult_id}/photos/
     * 
     * Sube una o más fotos para una consulta específica usando el consultId
     * generado previamente en /api/consult/registry/
     * 
     * Las fotos se envían en formato Base64 dentro del JSON
     * 
     * Headers automáticos:
     * - Content-Type: application/json (agregado por Feign)
     * - Authorization: Bearer <token> (agregado por FeignAuthInterceptor)
     * 
     * Request JSON (ejemplo con 2 fotos):
     * {
     *   "photos": [
     *     {
     *       "filename": "lesion1.jpg",
     *       "contentType": "image/jpeg",
     *       "data": "/9j/4AAQSkZJRgABAQAAAQABAAD...base64string..."
     *     },
     *     {
     *       "filename": "lesion2.jpg",
     *       "contentType": "image/jpeg",
     *       "data": "/9j/4AAQSkZJRgABAQAAAQABAAD...otrostringbase64..."
     *     }
     *   ]
     * }
     * 
     * Response JSON (éxito):
     * {
     *   "code": 200,
     *   "status": "ok",
     *   "mensaje": "Fotos subidas correctamente",
     *   "photoUrls": [
     *     "https://s3.amazonaws.com/elara/consultas/550e8400.../lesion1.jpg",
     *     "https://s3.amazonaws.com/elara/consultas/550e8400.../lesion2.jpg"
     *   ],
     *   "totalUploaded": 2
     * }
     * 
     * @param consultId ID de la consulta (obtenido de /api/consult/registry/)
     * @param request Objeto con el array de fotos en Base64
     * @return Respuesta con las URLs de las fotos subidas
     */
    @PostMapping("/api/consult/{consult_id}/photos/")
    UploadPhotosResponse uploadPhotos(
            @PathVariable("consult_id") String consultId,
            @RequestBody UploadPhotosRequest request
    );
}
