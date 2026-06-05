package ec.edu.epn.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.epn.dto.AirportRequest;
import ec.edu.epn.model.Airport;
import ec.edu.epn.repository.AirportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AirportControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AirportRepository airportRepository;

    // Limpia la base de datos H2 antes de cada prueba
    @BeforeEach
    void setUp() {
        airportRepository.deleteAll();
    }

    // ─── Métodos auxiliares privados ─────────────────────────────────────────

    private AirportRequest crearRequest(String nombre, String codigo, String ciudad, String pais) {
        AirportRequest req = new AirportRequest();
        req.setName(nombre);
        req.setCode(codigo);
        req.setCity(ciudad);
        req.setCountry(pais);
        return req;
    }

    private Airport crearAeropuerto(String nombre, String codigo, String ciudad, String pais) throws Exception {
        AirportRequest req = crearRequest(nombre, codigo, ciudad, pais);
        MvcResult resultado = mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(resultado.getResponse().getContentAsString(), Airport.class);
    }

    // ─── Pruebas ─────────────────────────────────────────────────────────────

    @Test
    void shouldCreateAirport() throws Exception {
        AirportRequest req = crearRequest("Mariscal Sucre", "UIO", "Quito", "Ecuador");

        mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Mariscal Sucre"))
                .andExpect(jsonPath("$.code").value("UIO"))
                .andExpect(jsonPath("$.city").value("Quito"))
                .andExpect(jsonPath("$.country").value("Ecuador"));
    }

    @Test
    void shouldRejectDuplicateAirportCode() throws Exception {
        crearAeropuerto("Mariscal Sucre", "UIO", "Quito", "Ecuador");

        // Intenta crear otro aeropuerto con el mismo código IATA → debe fallar
        AirportRequest duplicado = crearRequest("Otro aeropuerto", "UIO", "Latacunga", "Ecuador");

        mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicado)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldFindAllAirports() throws Exception {
        crearAeropuerto("Mariscal Sucre", "UIO", "Quito",  "Ecuador");
        crearAeropuerto("El Dorado",      "BOG", "Bogotá", "Colombia");

        mockMvc.perform(get("/api/airports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("UIO", "BOG")));
    }

    @Test
    void shouldFindAirportById() throws Exception {
        Airport creado = crearAeropuerto("Mariscal Sucre", "UIO", "Quito", "Ecuador");

        mockMvc.perform(get("/api/airports/{id}", creado.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(creado.getId()))
                .andExpect(jsonPath("$.code").value("UIO"))
                .andExpect(jsonPath("$.city").value("Quito"));
    }

    @Test
    void shouldFindAirportByCode() throws Exception {
        crearAeropuerto("Mariscal Sucre", "UIO", "Quito", "Ecuador");

        mockMvc.perform(get("/api/airports/code/{code}", "UIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("UIO"))
                .andExpect(jsonPath("$.country").value("Ecuador"));
    }

    @Test
    void shouldReturn404WhenAirportNotFound() throws Exception {
        mockMvc.perform(get("/api/airports/{id}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void shouldUpdateAirport() throws Exception {
        Airport creado = crearAeropuerto("Nombre Viejo", "UIO", "Quito", "Ecuador");

        AirportRequest actualizacion = crearRequest("Mariscal Sucre Internacional", "UIO", "Quito", "Ecuador");

        mockMvc.perform(put("/api/airports/{id}", creado.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actualizacion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(creado.getId()))
                .andExpect(jsonPath("$.name").value("Mariscal Sucre Internacional"));
    }

    @Test
    void shouldDeleteAirport() throws Exception {
        Airport creado = crearAeropuerto("Mariscal Sucre", "UIO", "Quito", "Ecuador");

        // Eliminar
        mockMvc.perform(delete("/api/airports/{id}", creado.getId()))
                .andExpect(status().isNoContent());

        // Verificar que ya no existe → 404
        mockMvc.perform(get("/api/airports/{id}", creado.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidAirportRequest() throws Exception {
        // Código IATA con solo 2 caracteres → inválido (debe tener exactamente 3)
        AirportRequest req = crearRequest("Mariscal Sucre", "UI", "Quito", "Ecuador");

        mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }
}
