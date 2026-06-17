package com.coruja.messaging;

import com.coruja.entity.Radars;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Publicador de mensagens para o RabbitMQ via SmallRye Reactive Messaging.

 * Canal configurado em application.properties:
 *   mp.messaging.outgoing.radares-out.*

 * Equivalente ao rabbitTemplate.convertAndSend() do Spring AMQP.
*/
@ApplicationScoped
@Slf4j
public class RadarMqPublisher {

    /**
     * "radares-out" é o nome do canal declarado em application.properties.
     * O conector RabbitMQ roteia para a exchange + routing key configurados.
     */
    @Inject
    @Channel("radares-out")
    Emitter<String> emitter;

    /**
     * Envia uma leitura de radar para o RabbitMQ de forma resiliente.
     * Falhas de envio são logadas mas NÃO lançam exceção (não deve
     * interromper o fluxo de persistência).
     */
    public void publicar(Radars radar) {
        if (!isValido(radar)) {
            log.warn("Dados incompletos para a placa {} - mensagem não enviada.", radar.getPlaca());
            return;
        }

        String mensagem = formatarMensagem(radar);

        try {
            emitter.send(mensagem);;
            log.info("Mensagem enviada ap RabbitMQ: {}", mensagem);
        } catch (Exception e) {
            log.warn("Falha ao enviar ao RabbitMQ - Placa: {} | Causa: {}", radar.getPlaca(), e.getMessage());
        }
    }

    private boolean isValido(Radars radar) {
        return  radar.getData() != null && !radar.getData().isBlank() &&
                radar.getHora() != null && !radar.getHora().isBlank() &&
                radar.getPlaca() != null && !radar.getPlaca().isBlank() &&
                radar.getLocal() != null && !radar.getLocal().isBlank() &&
                radar.getSentido() != null && !radar.getSentido().isBlank();
    }

    /**
     * Formato da mensagem: SPVias|data|hora|placa|Local|sentido
     * Mantido idêntico ao formato original do Spring.
     */
    private String formatarMensagem(Radars radar) {
        return  String.format("SPVias|%s|%s|%s|%s|%s",
                radar.getData(), radar.getHora(), radar.getPlaca(), radar.getLocal(),
                radar.getSentido()
        );
    }

}
