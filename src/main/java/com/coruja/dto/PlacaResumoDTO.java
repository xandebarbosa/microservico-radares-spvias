package com.coruja.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacaResumoDTO {

    /** Quantidade total de passagnes registradas */
    private long totalPassagens;

    /**Data da passagem mais antiga*/
    private String primeiraPassagemData;

    /** Data da passagem mais recente */
    private String ultimaPassagemData;

    /** Pracas distintas em que a placa foi detectada*/
    private List<String> pracas;

    /** Sentidos distintos registrados */
    private List<String> sentidos;
}
