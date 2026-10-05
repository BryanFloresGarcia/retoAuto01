# Estrategias de Pruebas - Deposito pasarela "Pago con QR"
## 1. Diseño de Casos de Prueba (Gherkin)
Se han cubierto los siguientes paths en `src/test/resources/features/deposito_pago_con_qr.feature`:
- **Happy Path**: Generacion exitosa de QR y flujo completo hasta estado Exitoso.
- **Alternative Path**: Montos validos dentro del rango (minimo/maximo).
- **Negative Path**: Montos fuera de rango, vacios o invalidos (validaciones de frontend/backend).
- **Exception Path**: Fallos del sistema / timeout controlado hacia la pasarela.
- **Security Path**: No reutilizacion del QR (prevencion de doble cobro/reuso).
- **Performance Path**: Generacion < 3s y estabilidad ante escenarios concurrentes/simultaneos.

Etiquetado:
- `@critical`: rutas transaccionales, seguridad y reglas de negocio criticas.
- `@smoke`: validacion minima de salud (Happy Path).
- `@regression`: cobertura ampliada (todos los paths).
- `@performance`: escenarios de carga/latencia para verificar SLA < 3s.

## 2. Estrategias de Pruebas (Funcionales y No Funcionales)

### Funcionales
- **Pruebas basadas en requerimientos**: validar flujo completo de deposito con QR (login, seleccion pasarela, monto, moneda, generacion QR, confirmacion, historial).
- **Pruebas de validacion de campos**: rangos (10-100 PEN), moneda obligatoria, formatos numericos.
- **Pruebas de transicion de estados**: Pendiente → Exitoso/Fallido/Caducado (oraculo independiente: UI historial + consulta SQL).
- **Pruebas de integracion**: autenticacion + modulo deposito + pasarela QR + registro en historial (capa aplicacion).
- **Pruebas de regresion**: cada commit que toque deposito debe pasar `@regression`.
- **Pruebas exploratorias**: flujos inesperados (cierre modal, refresco, multiples ventanas).

### No Funcionales
- **Rendimiento/Latencia**: tiempo de generacion QR < 3s (criterio exigido).
- **Estabilidad**: resistencia ante multiples solicitudes (simultaneas) sin degradacion severa.
- **Seguridad**: inmutabilidad/reutilizacion de QR, aislamiento de sesion, no exponicion de datos sensibles en logs.
- **Usabilidad**: QR visible/estable, mensajes claros, estados trazables.
- **Confiabilidad/Disponibilidad**: manejo de timeouts, reintentos controlados, degradacion elegante (mensaje amigable).
- **Observabilidad**: capturas de evidencia (screenshots), trazas para debugging.

## 3. Herramientas a Utilizar
- **Automatizacion UI**: Selenium WebDriver 4 (estabilidad, Selenium Manager). Abstraccion POM + Page Components.
- **Lenguaje/Framework**: Java 17 + Spring Boot 3 (IoC, configuracion tipada, inyeccion, paralelismo thread-safe).
- **BDD**: Cucumber 7 + Gherkin (trazabilidad req → test → evidencia).
- **Ejecutor**: JUnit Platform Suite + Maven Surefire/Failsafe (perfilado por tags, paralelismo dinámico).
- **Aserciones**: AssertJ (legibles, fluent).
- **Gestión de drivers**: Selenium Manager (nativo 4.6+). Sin WebDriverManager innecesario (evita conflictos).
- **Reporting**: Cucumber HTML/JSON (integrable a Jenkins/Allure).
- **BD**: SQL Server (validacion de datos persistidos: tablas `Depositos`, `Usuarios`).
- **Performance**: JMeter/Gatling/k6 (para evaluar concurrencia/punto de quiebre). En este framework se proveen escenarios `@performance` como base para carga.

## 4. Técnicas de Pruebas Funcionales de Caja Negra
- **Particion Equivalente (EP)**: clases de montos {<10 (invalido), 10-100 (valido), >100 (invalido)}; moneda {PEN/OTRA}.
- **Analisis de Valores Limites (BVA)**: limites 9.99, 10.00, 100.00, 100.01 PEN (min/max incluidos/excluidos).
- **Tabla de Decisiones**: combinaciones estado autenticado, pasarela QR, monto valido, moneda soportada → generar QR/Pendiente.
- **Transicion de Estados**: QR Pendiente → Exitoso (confirmacion) / Fallido (error) / Caducado (timeout/reuso).
- **Casos de Uso / Flujo Basado en Escenarios**: Happy/Alternative/Negative/Exception/Security (cubiertos en Gherkin).
- **Error Guessing**: caracteres no numericos, montos negativos, decimales, refresco durante generacion, cierre/reapertura modal.

**Ejemplo (BVA + EP):**
- EP invalido: 5.00 → rechazo.
- BVA frontera inferior valida: 10.00 → aceptado, genera QR.
- EP valido intermedio: 50.00 → aceptado.
- BVA frontera superior valida: 100.00 → aceptado.
- EP invalido superior: 150.00 → rechazo.

## 5. Evaluación Técnica de Performance (Proyecto: Deposito pasarela "Pago con QR")
Objetivo: validar que la generacion del QR cumple SLA < 3s y determinar comportamiento bajo carga.

### Alcance
- Generacion de QR (endpoint/flujo UI que renderiza QR).
- Confirmacion de pago (transicion estado) — latencia aceptable acotada.
- Historial — lectura.

### Metodologia
1. **Baseline (1 usuario)**: medir tiempo generacion QR (P50/P95/P99). Objetivo P95 < 3000ms.
2. **Carga incremental**: ramp-up 0→N usuarios (5,10,25,50,100) con think time realista (3-5s entre acciones).
3. **Carga sostenida**: N usuarios por 10-30 min (estabilidad, leaks memoria).
4. **Stress/Spike**: pico repentino (x2-x3 usuarios) para detectar colapso.
5. **Soak**: 1h para fugas de recursos (memoria, conexiones).
6. **Monitoreo**: CPU, memoria, I/O, threads, tiempos de respuesta, errores, throughput (TPS).

### Criterios de Aceptacion (SLA)
- Tiempo generacion QR < 3000ms (P95). P99 < 3500ms (margen).
- Error rate < 1% bajo carga nominal.
- Throughput estable, sin degradacion no lineal brusca.
- UI estable (QR visible, sin reflows bloqueantes).

### Herramientas Sugeridas
- **k6** (scriptable JS, CI-friendly), **Gatling** (Scala, reports detallados), **JMeter** (GUI+CLI). Para UI: Selenium Grid + pruebas de carga distribuida.

## 6. Métricas para Determinar el Punto de Quiebre (Breaking Point)
Definicion: carga maxima (usuarios concurrentes/TPS) donde el sistema mantiene SLA y error rate aceptable, superada la cual degrada bruscamente.

### Métricas Clave (Golden Signals)
- **Latencia**: Response Time P50, P90, P95, P99 (ms). Umbral critico P95 >= 3000ms sostenido.
- **Throughput**: TPS/RPS (operaciones exitosas/seg). Buscar plateau/caida.
- **Errores**: % errores (4xx/5xx/timeouts). Punto de quiebre cuando >1-2% sostenido o aumentan exponencialmente.
- **Saturacion**: CPU (%), Memoria (%), Heap, conexiones BD, colas, threads bloqueados, GC time.
- **Disponibilidad/Estabilidad**: timeouts, failed transactions rate.

### Umbrales para Detectarlo
- P95 de generacion QR supera 3000ms por >30s consecutivos.
- Tasa de error supera 5% sostenido (o 10% pico) con incremento de carga.
- Throughput deja de escalar linealmente (plateau) y luego cae mientras usuarios suben (inflexion).
- CPU > 80% sostenido + latencia creciente (backpressure).
- GC frecuente/stop-the-world que impacta P95.
- Timeouts aumentan abruptamente.

### Proceso
1. Incrementar usuarios paso a paso (ramp-up lento).
2. Graficar Usuarios vs P95 vs Throughput vs %Errores.
3. Identificar punto donde curva P95 se vuelve asintotica/vertical (knee point).
4. Confirmar con corrida sostenida 5-10 min en ese umbral.
5. Reportar: Punto de Quiebre (usuarios), Capacidad Nominal (usuarios con margen 20-30%), SLA vigente.

**Conclusión recomendada**: capacidad nominal = carga donde P95 < 3000ms, error < 1%, saturacion < 70%. Punto de quiebre = primer umbral sostenido que viola alguno.