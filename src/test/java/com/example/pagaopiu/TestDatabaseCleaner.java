package com.example.pagaopiu;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TestDatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public TestDatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        jdbcTemplate.execute("DELETE FROM incidencia_hermes");
        jdbcTemplate.execute("DELETE FROM entrega_hermes");
        jdbcTemplate.execute("DELETE FROM caja_sucursal");
        jdbcTemplate.execute("DELETE FROM cuota");
        jdbcTemplate.execute("DELETE FROM pago");
        jdbcTemplate.execute("DELETE FROM prestamo");
        jdbcTemplate.execute("DELETE FROM cliente");
    }
}
