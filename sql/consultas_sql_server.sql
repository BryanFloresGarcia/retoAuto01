-- =====================================================
-- Reto Tecnico: Consultas en SQL Server (Anexo 2)
-- Tablas: Depositos (IdDeposito, UsuarioId, Monto, Moneda, Estado, FechaCreacion)
--         Usuarios  (IdUsuario, NombreUsuario, Email)
-- =====================================================

-- 8. Caso de prueba: Verificar depósitos consultando directamente a la tabla Depositos
-- Criterios:
-- - Ordenar mostrando primero el último registro generado
-- - Montos entre 10 y 100
-- - Estado del depósito "Exitoso"
-- - Moneda Soles (PEN)
-- - Tabla Depositos
SELECT
    IdDeposito,
    UsuarioId,
    Monto,
    Moneda,
    Estado,
    FechaCreacion
FROM
    dbo.Depositos
WHERE
    Monto BETWEEN 10 AND 100
  AND Estado = 'Exitoso'
  AND Moneda = 'PEN'
ORDER BY
    FechaCreacion DESC,
    IdDeposito DESC;

-- 9. Caso de prueba: Verificar depósitos relacionados con la tabla Usuarios (INNER JOIN)
-- Criterios:
-- - Usuario Testcalimaco35
-- - Ordenar mostrando primero el último registro generado
-- - Montos de 100
-- - Estado del depósito "Pendiente"
-- - Moneda Soles (PEN)
-- - Tablas: Depositos y Usuarios
SELECT
    d.IdDeposito,
    d.UsuarioId,
    u.NombreUsuario,
    u.Email,
    d.Monto,
    d.Moneda,
    d.Estado,
    d.FechaCreacion
FROM
    dbo.Depositos d
    INNER JOIN dbo.Usuarios u ON d.UsuarioId = u.IdUsuario
WHERE
    u.NombreUsuario = 'Testcalimaco35'
  AND d.Monto = 100.00
  AND d.Estado = 'Pendiente'
  AND d.Moneda = 'PEN'
ORDER BY
    d.FechaCreacion DESC,
    d.IdDeposito DESC;

-- =====================================================
-- Notas:
-- - Uso de BETWEEN para abarcar montos 10 y 100 inclusive.
-- - ORDER BY por FechaCreacion DESC priorizando el ultimo registro generado.
-- - INNER JOIN garantiza solo depositos con usuario existente.
-- - No se usan SELECT * para mantener consultas legibles y mantenibles.
-- =====================================================