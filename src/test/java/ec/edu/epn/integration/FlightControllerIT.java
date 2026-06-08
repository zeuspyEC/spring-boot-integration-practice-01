package ec.edu.epn.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.epn.dto.AirportRequest;
import ec.edu.epn.dto.FlightRequest;
import ec.edu.epn.model.Airport;
import ec.edu.epn.model.Flight;
import ec.edu.epn.repository.AirportRepository;
import ec.edu.epn.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class FlightControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private FlightRepository flightRepository;

    // IDs de aeropuertos reutilizados en cada prueba
    private Long origenId;
    private Long destinoId;

    // Fechas base fijas para todos los tests
    private static final LocalDateTime SALIDA  = LocalDateTime.of(2025, 9, 15, 8, 0);
    private static final LocalDateTime LLEGADA = LocalDateTime.of(2025, 9, 15, 10, 30);

    // Crea aeropuertos de origen y destino antes de cada prueba
    @BeforeEach
    void setUp() throws Exception {
        flightRepository.deleteAll();
        airportRepository.deleteAll();

        origenId  = crearAeropuerto("Mariscal Sucre", "UIO", "Quito",  "Ecuador").getId();
        destinoId = crearAeropuerto("El Dorado",      "BOG", "Bogotá", "Colombia").getId();
    }

    // ─── Métodos auxiliares privados ─────────────────────────────────────────

    private Airport crearAeropuerto(String nombre, String codigo, String ciudad, String pais) throws Exception {
        AirportRequest req = new AirportRequest();
        req.setName(nombre);
        req.setCode(codigo);
        req.setCity(ciudad);
        req.setCountry(pais);
        MvcResult r = mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(r.getResponse().getContentAsString(), Airport.class);
    }
    private FlightRequest crearRequest(String numero, Long origId, Long destId, LocalDateTime salida, LocalDateTime llegada, String estado) {
        FlightRequest req = new FlightRequest();
        req.setFlightNumber(numero);
        req.setOriginId(origId);
        req.setDestinationId(destId);
        req.setDepartureTime(salida);
        req.setArrivalTime(llegada);
        req.setStatus(estado);
        return req;
    }
    private Flight crearVuelo(String numero) throws Exception {
        FlightRequest req = crearRequest(numero, origenId, destinoId, SALIDA, LLEGADA, "SCHEDULED");
        MvcResult r = mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(r.getResponse().getContentAsString(), Flight.class);
    }
    // ─── Pruebas ─────────────────────────────────────────────────────────────

    @Test
    void shouldCreateFlight() throws Exception {
        FlightRequest req = crearRequest("AV101", origenId, destinoId, SALIDA, LLEGADA, "SCHEDULED");

        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.flightNumber").value("AV101"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.origin.code").value("UIO"))
                .andExpect(jsonPath("$.destination.code").value("BOG"));
    }
}
