package cl.duoc.cloud.cuentas.repo;

import cl.duoc.cloud.cuentas.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

@Repository
public class CuentasRepository {
 private final JdbcTemplate jdbc; public CuentasRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Optional<CuentaResponse> cuenta(Long id){
  return jdbc.query("SELECT c.*,COALESCE(e.estado,'ACTIVA') estado FROM cuentas c LEFT JOIN cuentas_estado e ON e.cuenta_id=c.cuenta_id WHERE c.cuenta_id=?",
   (rs,i)->new CuentaResponse(rs.getLong("cuenta_id"),rs.getString("nombre"),rs.getBigDecimal("saldo"),rs.getInt("edad"),rs.getString("tipo_cuenta"),rs.getTimestamp("fecha_actualizacion").toLocalDateTime(),rs.getString("estado")),id).stream().findFirst();
 }
 public void insertar(AperturaCuentaRequest r){
  jdbc.update("INSERT INTO cuentas(cuenta_id,nombre,saldo,edad,tipo_cuenta) VALUES(?,?,0,?,?)",r.cuentaId(),r.nombre().trim(),r.edad(),r.tipoCuenta());
 }
 public void registrarApertura(Long id){
  jdbc.update("INSERT INTO cuentas_estado(cuenta_id,estado) VALUES(?,'ACTIVA')",id);
 }
 public Optional<BigDecimal> saldoParaActualizar(Long id){
  return jdbc.query("SELECT saldo FROM cuentas WHERE cuenta_id=? FOR UPDATE",(rs,i)->rs.getBigDecimal("saldo"),id).stream().findFirst();
 }
 public String estadoCuenta(Long id){
  return jdbc.query("SELECT estado FROM cuentas_estado WHERE cuenta_id=? FOR UPDATE",(rs,i)->rs.getString("estado"),id).stream().findFirst().orElse("ACTIVA");
 }
 public void actualizarTipo(Long id,String tipo){
  jdbc.update("UPDATE cuentas SET tipo_cuenta=?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE cuenta_id=?",tipo,id);
 }
 public void registrarCierre(Long id){
  if(jdbc.update("UPDATE cuentas_estado SET estado='CERRADA',fecha_cierre=CURRENT_TIMESTAMP WHERE cuenta_id=?",id)==0){
   jdbc.update("INSERT INTO cuentas_estado(cuenta_id,estado,fecha_cierre) VALUES(?,'CERRADA',CURRENT_TIMESTAMP)",id);
  }
  jdbc.update("UPDATE cuentas SET fecha_actualizacion=CURRENT_TIMESTAMP WHERE cuenta_id=?",id);
 }
 public Optional<InteresResponse> interes(Long id){
  return jdbc.query("SELECT periodo,saldo_inicial,tasa_interes,interes_calculado,saldo_final,archivo_origen FROM intereses_calculados WHERE cuenta_id=? ORDER BY fecha_calculo DESC,id DESC LIMIT 1",
   (rs,i)->new InteresResponse(rs.getString("periodo"),rs.getBigDecimal("saldo_inicial"),rs.getBigDecimal("tasa_interes"),rs.getBigDecimal("interes_calculado"),rs.getBigDecimal("saldo_final"),rs.getString("archivo_origen")),id).stream().findFirst();
 }
 public Optional<EstadoAnualResponse> estado(Long id){
  return jdbc.query("SELECT anio,cantidad_movimientos,total_depositos,total_retiros,total_compras,total_pagos,saldo_anual,archivo_origen FROM estados_cuenta_anuales WHERE cuenta_id=? ORDER BY anio DESC,fecha_generacion DESC,id DESC LIMIT 1",
   (rs,i)->new EstadoAnualResponse(rs.getInt("anio"),rs.getInt("cantidad_movimientos"),rs.getBigDecimal("total_depositos"),rs.getBigDecimal("total_retiros"),rs.getBigDecimal("total_compras"),rs.getBigDecimal("total_pagos"),rs.getBigDecimal("saldo_anual"),rs.getString("archivo_origen")),id).stream().findFirst();
 }
 public ResumenResponse resumen(){
  return jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM cuentas) cuentas,(SELECT COUNT(*) FROM transacciones_procesadas) transacciones,(SELECT COUNT(*) FROM movimientos_anuales_procesados) movimientos,(SELECT COUNT(*) FROM registros_rechazados) rechazos,COALESCE((SELECT SUM(saldo) FROM cuentas),0) saldo_total",
   (rs,i)->new ResumenResponse(rs.getLong("cuentas"),rs.getLong("transacciones"),rs.getLong("movimientos"),rs.getLong("rechazos"),rs.getBigDecimal("saldo_total")));
 }
}

