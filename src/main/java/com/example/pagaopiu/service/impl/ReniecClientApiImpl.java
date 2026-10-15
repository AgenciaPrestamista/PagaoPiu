package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.dto.reniec.ReniecApiRequest;
import com.example.pagaopiu.dto.reniec.ReniecApiResponse;
import com.example.pagaopiu.model.entity.ReniecPadron;
import com.example.pagaopiu.repository.ReniecPadronRepository;
import com.example.pagaopiu.service.ReniecClient;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service("reniecClientApi")
@ConditionalOnProperty(name = "app.reniec.provider", havingValue = "api")
public class ReniecClientApiImpl implements ReniecClient {

    private final RestClient restClient;
    private final ReniecPadronRepository reniecPadronRepository;

    public ReniecClientApiImpl(
            @Value("${app.reniec.api.url:https://api.json.pe/api/dni}") String apiUrl,
            @Value("${app.reniec.api.token:}") String apiToken,
            ReniecPadronRepository reniecPadronRepository) {
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.reniecPadronRepository = reniecPadronRepository;
    }

    @Override
    public Optional<ReniecPadron> buscarPorDni(String dni) {
        try {
            ReniecApiResponse response = restClient.post()
                    .body(new ReniecApiRequest(dni))
                    .retrieve()
                    .body(ReniecApiResponse.class);

            if (response != null && response.success() && response.data() != null && response.data().nombreCompleto() != null) {
                return Optional.of(ReniecPadron.builder()
                        .dni(response.data().numero())
                        .nombreCompleto(response.data().nombreCompleto())
                        .nacionalidad("PERUANA")
                        .dniVigente(true)
                        .fallecido(false)
                        .build());
            }
        } catch (Exception ignored) {
        }
        return reniecPadronRepository.findByDni(dni);
    }
}
