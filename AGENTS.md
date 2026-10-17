# AGENTS.md — EventCart

> Contexto y guía de ejecución para Codex/OpenCode. Este documento registra decisiones de producto y objetivos de aprendizaje; no autoriza ampliar el alcance ni sustituye inspeccionar el repositorio. Prioridad: un sistema funcional, verificable y explicable, no una arquitectura espectacular sin producto.

## 1. Propósito

**EventCart — Limited Commerce Engine** es una aplicación de comercio para **lanzamientos programados de productos de disponibilidad limitada**. Un organizador publica una venta, compradores concurrentes reservan unidades por tiempo limitado y concluyen un **checkout simulado**. La aplicación debe conservar consistencia de inventario y pedidos ante peticiones simultáneas, eventos repetidos y fallos parciales.

**Prioridad explícita:** portafolio y aprendizaje profundo de **Apache Kafka — Nivel 2: ingeniería distribuida aplicada**, por encima de validación comercial inmediata. Aun así, construir una experiencia de compra coherente; no convertirlo en un mero panel de Kafka.

**Primer caso de uso:** **productos físicos** con cantidad finita. El motor puede permitir después **entradas generales para eventos** (sin asientos numerados), pero NO implementar ticketing/QR en V1 ni abstraer prematuramente todos los tipos posibles de productos.

No competir inicialmente con Shopify/Ticketmaster; no afirmar que la arquitectura garantiza escalado ilimitado o que existe demanda comercial validada.

## 2. Decisiones cerradas

1. Proyecto denominado **EventCart**.
2. **Motor comercial común**, implementado y demostrado inicialmente con **un producto físico por lanzamiento**. Admitir múltiples lanzamientos sin necesidad de carritos multiartículo.
3. Lanzamiento con título, producto, precio ficticio/de referencia, stock inicial, apertura y cierre configurables, y límite por comprador si se necesita en el primer flujo.
4. Reservas temporales con expiración; checkout **simulado**, con resultados aprobado, rechazado y fallo/timeout, sin mover dinero real.
5. Asegurar no sobreventa mediante **persistencia y operaciones atómicas en PostgreSQL**, NO mediante Kafka ni contadores del frontend.
6. Kafka debe servir para eventos de negocio y procesamiento asíncrono real, **no** para toda lectura/escritura. Aplicar **Transactional Outbox**, consumidores idempotentes, reintentos controlados y **DLQ** o procedimiento equivalente para fallos no recuperables.
7. Demostración en **dos modos**: recorrido comercial visible y laboratorio técnico con pruebas reproducibles de concurrencia/fallos. Las inyecciones de fallos son de desarrollo y no están expuestas públicamente.
8. Diseñar para evolución posible a entradas generales, pero **no** implementar entradas, QR, control de acceso, asientos, reembolsos ni colas virtuales en el primer lanzamiento.
9. No incorporar pagos reales, logística, ERP, facturación ni servicios empresariales por anticipación.
10. Decisiones sobre event sourcing, Kafka Streams, microservicios y particionado avanzado **no forman parte del Nivel 2 obligatorio**; pueden estudiarse posteriormente.

## 3. Actores y experiencias

- **Organizador/vendedor:** crea y programa un lanzamiento, configura stock y condiciones, lo publica/cancela según reglas, consulta reservas, pedidos y disponibilidad.
- **Comprador:** ve lanzamientos publicados; cuando abre la venta solicita reservar unidades y recibe reserva con caducidad o rechazo claro; completa checkout simulado y consulta resultado.
- **Operador técnico (laboratorio):** observa estado de productores/consumidores, procesamiento de eventos, reintentos y errores, y ejecuta scripts de carga y fallos desde entorno de desarrollo. No construir un sustituto integral de herramientas de observabilidad.

Identidad mínima apropiada al prototipo; **no** inventar permisos/roles empresariales, marketplaces multi-tenant ni perfiles extensos. Identificar compradores de modo que se puedan validar límites y peticiones repetidas sin usar identidad arbitraria suministrada por el cliente como autorización.

## 4. Flujo principal e invariantes

Flujo observable: **crear lanzamiento → programar/publicar → abrir venta → reservar stock de forma atómica → checkout simulado → confirmar pedido o liberar reserva → emitir eventos y actualizar proyecciones/notificaciones**.

### Estado de venta y existencias

- Distinguir unidades **disponibles, reservadas y confirmadas/vendidas** con definiciones precisas. Invariante esencial, para cada lanzamiento: **disponibles + reservadas activas + confirmadas = stock total** (o su equivalente contable explícito si reservas caducadas aún esperan reconciliación); ningún valor negativo ni ventas por encima del total.
- Reserva y descuento de disponibilidad ocurren en **una transacción**. Usar bloqueo/actualización condicional y restricciones de BD; reintentos no duplican reservas.
- La apertura/cierre se autoriza en el servidor con hora y zona horaria explícitas. El frontend no es fuente de verdad del stock ni de la disponibilidad.
- Definir estados pequeños y transiciones válidas, por ejemplo: `RESERVED -> CONFIRMED | RELEASED | EXPIRED`, con el tratamiento explícito de checkout pendiente. Evitar transiciones desde estados terminales o confirmar reservas caducadas sin una política decidida.
- Expiración basada en `expires_at` persistente, procesamiento periódico y reconciliación. **La expiración correcta no puede depender únicamente de que un consumidor Kafka esté activo.** Garantizar liberación una sola vez incluso bajo carreras con confirmaciones.
- Checkout simulado determinista/configurable para reproducir resultados, lentitud y fallos. Evitar confirmar compras cuando un timeout deja un resultado indeterminado; modelar reconciliación/estado pendiente de forma explícita.
- Importe y cantidad se calculan/revalidan en backend; almacenar datos comerciales necesarios en el pedido para evitar que ediciones posteriores cambien su historia.
- Cancelación de lanzamiento y cierre de venta deben tratar las reservas activas explícitamente; no implementar casos complejos antes de cerrar el flujo base.

### Integridad de entrada

- Claves de idempotencia para solicitudes de reserva/checkout con alcance y vencimiento definidos; devolver el mismo resultado para reintentos equivalentes y rechazar la misma clave con contenido incompatible.
- Proteger limites por comprador si se habilitan; consultar/actualizar atómicamente para evitar eludirlos mediante concurrencia.
- Validar cantidades, autorización, estados y fechas en servidor; controlar rate limits básicos y evitar ejecución ilimitada de simulaciones de carga en despliegues públicos.

## 5. Kafka y confiabilidad — alcance Nivel 2

### Producción

- Producir eventos por cambios de estado del negocio que **realmente se hayan comprometido** (por ejemplo `ReservationCreated`, `ReservationExpired`, `OrderConfirmed`, `PaymentSimulationFailed`). Definir **contrato versionado** con `event_id`, tipo, versión, `aggregate_id`, instante y payload acotado.
- **Transactional Outbox:** registrar cambio de estado y fila outbox en la **misma transacción PostgreSQL**; un publicador separado entrega a Kafka, registra intentos y permite recuperación. Aceptar posibles entregas duplicadas: los consumidores son idempotentes.
- Elegir claves/particiones Kafka para preservar orden relevante **por agregado** cuando sea necesario; no suponer orden global entre particiones o topics.

### Consumo

- Implementar consumidores separados lógicamente (pueden compartir despliegue al inicio) con efectos observables: por ejemplo, confirmaciones/notificaciones ficticias, proyección de ventas y auditoría operativa. No crear consumidores que solo imprimen logs como totalidad del proyecto.
- Deduplicación persistente por `event_id`/consumidor y efectos idempotentes. Manejo explícito de eventos desconocidos, versiones incompatibles y operaciones fallidas.
- Reintentos limitados con backoff; eventos irrecuperables a DLQ/circuito de investigación documentado. Métricas y herramientas para inspección/reproceso controlado, sin reprocesar ciegamente con riesgo de duplicar efectos.
- Reconocer semántica **at-least-once** y límites de garantías transaccionales de Kafka sobre efectos en PostgreSQL u otros sistemas. No declarar “exactly once end-to-end” sin demostrarlo.
- La fuente de verdad del stock, reservas y pedidos es PostgreSQL; Kafka transporta hechos/derivaciones asíncronas. Las proyecciones pueden tener retraso visible.

### Procesos de fallo

Probar caída de consumidor, duplicados, redelivery, reinicio durante publicación outbox, error de checkout, expiración simultánea con checkout, eventos fuera de orden cuando aplique, y recuperación eventual. Inyección de fallos únicamente en local/laboratorio.

## 6. Producto/UI V1

- **Vista de comprador:** lanzamiento, producto, precio, stock/estado informativo, tiempo hasta apertura, solicitud de reserva, contador de caducidad **informativo** y checkout simulado con estados claros. No prometer stock confirmado solo por animación/contador cliente.
- **Vista organizador:** CRUD mínimo de lanzamientos, publicación/programación, inventario y métricas de reservas/ventas; reglas de cambio tras comenzar la venta.
- **Vista laboratorio:** estado de procesamiento, incidencias, duplicados detectados, DLQ o exploración con herramientas existentes, métricas de carga y consistencia. Mantener scripts como fuente de verdad de las pruebas, no inventar un simulador en el navegador.
- Actualizaciones en vivo con polling/SSE inicialmente; WebSockets solo si el caso lo justifica.
- UX clara cuando la reserva caduca, el checkout está pendiente, el sistema está degradado o se agota el stock.

## 7. Stack y dirección arquitectónica

**Punto de partida:** Java LTS compatible (preferencia Java 25) + versión estable de Spring Boot con Spring Kafka; **PostgreSQL**, **Apache Kafka**, **React + TypeScript**; **JUnit, Testcontainers y k6** (o equivalente) para carga; Docker Compose local.

Preferir un **monolito modular** con API HTTP, persistencia y componentes de eventos, y consumidores aislables. Se pueden separar procesos si las pruebas de Kafka lo hacen instructivo, **sin imponer varios microservicios al inicio**. No crear un repositorio de servicios vacíos.

```text
React/TypeScript (comprador · organizador · laboratorio mínimo)
                      |
             Spring Boot API
      Launches / Reservations / Checkout / Orders
                      |
          PostgreSQL + Transactional Outbox
                      |
              Outbox Publisher
                      v
                    Kafka
          /            |             \
   Notification    Sales Projection   Audit/Operations
    Consumer         Consumer          Consumer
```

Separar límites del dominio y transacciones de detalles de Kafka; el cálculo de stock no usa proyecciones asíncronas. No añadir Redis, Kafka Streams, Kubernetes, saga framework, event sourcing, CQRS formal ni observabilidad empresarial completa sin necesidad demostrada. Para monitorización, emplear herramientas existentes cuando sean suficientes.

## 8. Pruebas, métricas y demostración

### Pruebas funcionales

- Unitarias sobre reglas y máquina de estados; integración con PostgreSQL real/Testcontainers para reservas atómicas, transiciones e idempotencia; Kafka/Testcontainers para outbox, consumidores y redelivery.
- Verificar que la ruta de error no confirma pedidos ni pierde/libera stock indebidamente.
- Pruebas de autorización y parámetros inválidos.

### Laboratorio de concurrencia

Escenario objetivo ilustrativo: **100 unidades iniciales y ~1.000 clientes virtuales concurrentes**. Ejecutar con k6 u otra herramienta reproducible. No presentarlo como benchmark ya logrado.

Comprobar al menos: (a) **cero sobreventas**, (b) reservas duplicadas prevenidas, (c) suma de stock consistente, (d) reservas caducadas liberadas una vez, (e) pedidos coherentes con pagos simulados, (f) consumidores reiniciados recuperan eventos pendientes sin duplicar efectos.

Medir **throughput**, latencias p50/p95/p99, tasa de errores, reservas aprobadas/rechazadas, tiempo de publicación outbox, lag de consumidores, duplicados y tiempo de recuperación. Publicar resultados con hardware, entorno, versión y limitaciones; nunca inventar métricas o afirmar escala en producción.

### Inyección de fallos

Scripts de desarrollo para detener/reiniciar consumidores o publicador, reproducir mensajes y simular checkout lento/rechazado. Ejecutar únicamente sobre componentes propios y entornos controlados. Documentar qué invariantes permanecen verdaderas.

**Definition of Done del primer producto:** crear lanzamiento, reservar de forma atómica, completar/rechazar checkout simulado, expirar reservas, confirmar pedidos y observar eventos consumidos; escenario reproducible demuestra los invariantes, el reinicio de consumidor recupera pendientes y el README permite reproducir la prueba localmente.

## 9. Secuencia de entrega — máxima prioridad a slices funcionales

1. **Primer vertical sin Kafka:** crear/publicar un lanzamiento físico, reservar unidades concurrentemente con PostgreSQL, checkout simulado y pruebas de no sobreventa. Así se valida primero la corrección del dominio.
2. **Outbox + Kafka:** emitir eventos reales mediante outbox transaccional, consumidores con efectos verificables, deduplicación e integración de pruebas.
3. **Caducidad y fallos:** reconciliación de reservas, reintentos, DLQ y pruebas sobre carreras checkout-expiración, reinicios y duplicados.
4. **Experiencia visible:** interfaces de comprador/organizador, estado en vivo donde aporte; vista operativa mínima.
5. **Laboratorio de portafolio:** k6, experimentos de fallo reproducibles, observabilidad/indicadores y documentación de resultados medidos.

No convertir estos hitos en cinco fases enormes ni bloquear implementación con ceremonias documentales. Ajustar secuencia cuando un slice integrado lo requiera; **el aprendizaje de Kafka es obligatorio, no una mejora opcional**.

## 10. Exclusiones V1

Sin ticketing ni QR, asientos numerados, pagos reales, antifraude/antibots avanzado, lista de espera empresarial, mapas de recinto, logística, devoluciones, facturación, carritos complejos, campañas de marketing, inventario ERP, marketplace multi-tenant, Kafka Streams, event sourcing, Kubernetes, ni microservicios impuestos para exhibición.

La posible modalidad futura de **entradas generales** se reconsidera **tras** validar el motor y sus invariantes. Un producto general y una entrada pueden necesitar procesos posteriores diferentes; no fingir que son idénticos.

## 11. Seguridad y prácticas operativas

- Secretos fuera del repositorio; configuraciones locales y sample env sin secretos. No incorporar números de tarjeta reales; simulación de pago con tokens ficticios.
- Validar permisos del organizador y del comprador; no filtrar datos personales en eventos, logs o métricas.
- Eventos solo contienen datos mínimos; versionar contratos y no introducir cambios incompatibles silenciosos.
- Controlar tiempos de espera, tamaños de payload y degradación de Kafka; la API comercial puede continuar con transacciones/outbox hasta límites operativos definidos, sin afirmar que publicación es inmediata.
- Mantener integridad y reconciliación ante reinicios; backups y despliegue real quedan fuera de requisitos inmediatos de portafolio salvo que se publique.

## 12. Reglas para agentes y próximo paso

- Antes de actuar: leer este archivo, examinar repo y distinguir **decisiones cerradas** de propuestas técnicas reversibles.
- Para cambios pequeños: especificación mínima → implementar → testear → demostrar. Reservar investigación profunda para garantías de inventario, diseño outbox, concurrencia y fallos observables.
- No encadenar brainstorming, UX Architecture, Visual Design y SDD extensos para tareas pequeñas. Máximo cinco hitos en planes iniciales y primer código funcional temprano.
- No construir muchas capas, workers, componentes o documentación vacíos antes del primer flujo transaccional.
- El agente puede resolver detalles locales; consultar al propietario si cambiará alcance, dominio, stack, invariantes, costos externos o implementación de múltiples servicios separados.
- Tras cada incremento: informar lo que funciona, cómo reproducirlo, qué garantías se probaron, métricas realmente observadas y siguiente paso mínimo.
- **Próximo paso:** inspeccionar repositorio y proponer secuencia breve para primer vertical, especialmente modelo de inventario/reserva, transición de checkout y pruebas concurrentes. Implementar tras aprobación del propietario; no iniciar Kafka antes de garantizar la transacción básica.

## 13. Decisiones abiertas deliberadamente

- Valor exacto de tiempo de reserva, límites de compra, políticas de apertura/cancelación.
- Política precisa para checkout pendiente cuando excede el vencimiento de reserva.
- Persistencia y protocolo del pago simulado; criterios para inyección controlada de fallos.
- Estructura de módulos/procesos Kafka, estrategia de particionado, esquema exacto de outbox/DLQ y tooling mínimo de observabilidad.
- Identidad y autenticación mínimas; UX/UI final y despliegue opcional de la demo.
- Momento y diseño del posible módulo de entradas generales, **sin compromiso en V1**.

---

**Principio rector:** EventCart debe demostrar con pruebas y fallos reproducibles que puede vender unidades limitadas sin sobreventa y procesar eventos de negocio correctamente en Kafka. Una experiencia usable más garantías medibles vale más que una arquitectura distribuida aparatosa.
