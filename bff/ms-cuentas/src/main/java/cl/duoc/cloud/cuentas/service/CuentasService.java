package cl.duoc.cloud.cuentas.service;

import cl.duoc.cloud.cuentas.config.ApiErrorHandler.ConflictoCuenta;
import cl.duoc.cloud.cuentas.config.ApiErrorHandler.RecursoNoEncontrado;
import cl.duoc.cloud.cuentas.model.*;
import cl.duoc.cloud.cuentas.repo.CuentasRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CuentasService {
    private final CuentasRepository repo;

    public CuentasService(CuentasRepository repo) { this.repo = repo; }

    @Transactional
    public CuentaResponse abrir(AperturaCuentaRequest solicitud) {
        try {
            // La PK protege también ante dos aperturas concurrentes del mismo ID.
            repo.insertar(solicitud);
        } catch (DuplicateKeyException ex) {
            throw new ConflictoCuenta("Ya existe la cuenta " + solicitud.cuentaId());
        }
        repo.registrarApertura(solicitud.cuentaId());
        return existente(solicitud.cuentaId());
    }

    @Transactional
    public CuentaResponse mantener(Long id, MantenimientoCuentaRequest solicitud) {
        bloquearExistente(id);
        if ("CERRADA".equals(repo.estadoCuenta(id))) {
            throw new ConflictoCuenta("No se puede modificar una cuenta cerrada");
        }
        repo.actualizarTipo(id, solicitud.tipoCuenta());
        return existente(id);
    }

    @Transactional
    public CuentaResponse cerrar(Long id) {
        BigDecimal saldo = bloquearExistente(id);
        // El segundo cierre devuelve el mismo estado sin borrar el historial.
        if (!"CERRADA".equals(repo.estadoCuenta(id))) {
            if (saldo.signum() != 0) {
                throw new ConflictoCuenta("La cuenta debe tener saldo cero antes del cierre");
            }
            repo.registrarCierre(id);
        }
        return existente(id);
    }

    private BigDecimal bloquearExistente(Long id) {
        return repo.saldoParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontrado("No existe la cuenta " + id));
    }

    private CuentaResponse existente(Long id) {
        return repo.cuenta(id)
                .orElseThrow(() -> new RecursoNoEncontrado("No existe la cuenta " + id));
    }
}
