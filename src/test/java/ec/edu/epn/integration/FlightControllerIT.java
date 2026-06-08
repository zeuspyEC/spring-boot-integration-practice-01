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
    @Test
    void shouldRejectDuplicateFlightNumber() throws Exception {
        crearVuelo("AV101");

        // Mismo número de vuelo → debe rechazar con 400
        FlightRequest duplicado = crearRequest("AV101", origenId, destinoId,
                SALIDA.plusDays(1), LLEGADA.plusDays(1), "SCHEDULED");

        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicado)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
    @Test
    void shouldRejectArrivalBeforeDeparture() throws Exception {
        // Llegada anterior a la salida → debe rechazar con 400
        FlightRequest req = crearRequest("AV202", origenId, destinoId,
                LLEGADA, SALIDA, "SCHEDULED"); // invertido: llegada < salida

        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }    
    @Test
    void shouldFindAllFlights() throws Exception {
        crearVuelo("AV101");
        crearVuelo("AV102");

        mockMvc.perform(get("/api/flights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].flightNumber", containsInAnyOrder("AV101", "AV102")));
    }    
    @Test
    void shouldFindFlightById() throws Exception {
        Flight creado = crearVuelo("AV101");

        mockMvc.perform(get("/api/flights/{id}", creado.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(creado.getId()))
                .andExpect(jsonPath("$.flightNumber").value("AV101"));
    }

    @Test
    void shouldFindFlightByFlightNumber() throws Exception {
        crearVuelo("AV101");

        mockMvc.perform(get("/api/flights/number/{numero}", "AV101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("AV101"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    } 
    @Test
    void shouldFindFlightsByStatus() throws Exception {
        crearVuelo("AV101"); // SCHEDULED

        // Crear un vuelo con estado diferente para confirmar que el filtro funciona
        FlightRequest retrasado = crearRequest("AV999", origenId, destinoId,
                SALIDA.plusDays(2), LLEGADA.plusDays(2), "DELAYED");
        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(retrasado)));

        mockMvc.perform(get("/api/flights/status/{estado}", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", everyItem(is("SCHEDULED"))));
    }      
    @Test
    void shouldFindFlightsBetweenDates() throws Exception {
        crearVuelo("AV101"); // salida = 2025-09-15T08:00

        LocalDateTime inicio = SALIDA.minusHours(1);
        LocalDateTime fin    = SALIDA.plusHours(1);

        mockMvc.perform(get("/api/flights/between")
                        .param("start", inicio.toString())
                        .param("end",   fin.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].flightNumber").value("AV101"));
    }
    @Test
    void shouldUpdateFlight() throws Exception {
        Flight creado = crearVuelo("AV101");

        // Actualizar el estado a IN_FLIGHT
        FlightRequest actualizacion = crearRequest("AV101", origenId, destinoId,
                SALIDA, LLEGADA, "IN_FLIGHT");

        mockMvc.perform(put("/api/flights/{id}", creado.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actualizacion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_FLIGHT"));
    }

    @Test
    void shouldReturn404WhenFlightNotFound() throws Exception {
        mockMvc.perform(get("/api/flights/{id}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void shouldDeleteFlight() throws Exception {
        Flight creado = crearVuelo("AV101");

        // Eliminar
        mockMvc.perform(delete("/api/flights/{id}", creado.getId()))
                .andExpect(status().isNoContent());

        // Verificar que ya no existe → 404
        mockMvc.perform(get("/api/flights/{id}", creado.getId()))
                .andExpect(status().isNotFound());
    }
}
