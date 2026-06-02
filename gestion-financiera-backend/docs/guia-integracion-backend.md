# Guía de integración del backend

**Proyecto:** eko - Gestion Financiera 
**Base URL local:** `http://localhost:8080`  
**Prefijo común de APIs:** `/api`

Este documento resume todos los endpoints expuestos por el backend para facilitar la integración con el frontend.

## Reglas generales

### Autenticación
La mayoría de los endpoints están protegidos por Spring Security y requieren un JWT en el header `Authorization`:

```http
Authorization: Bearer <jwt>
```

### CORS
CORS está habilitado globalmente para aceptar peticiones desde cualquier origen por ahora. También se permiten las solicitudes preflight `OPTIONS`.

### Formato de errores
Hay dos formatos de error principales:

1. Errores lanzados por la aplicación (`ResponseStatusException`):

```json
{
  "timestamp": "2026-06-01T12:34:56.789",
  "status": 400,
  "error": "Bad Request",
  "message": "Tipo de exportación no válido. Use 'pdf' o 'csv'.",
  "path": "/api/reports/export"
}
```

2. Errores de seguridad cuando falta o es inválido el JWT:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "No autorizado",
  "path": "/api/categorias/usuario"
}
```

### Códigos de estado más comunes
- `200 OK`: consulta o actualización exitosa.
- `201 Created`: recurso creado correctamente.
- `204 No Content`: eliminación exitosa sin cuerpo.
- `400 Bad Request`: datos inválidos, parámetros faltantes o reglas de negocio.
- `401 Unauthorized`: token ausente o inválido.
- `403 Forbidden`: el usuario no tiene permisos sobre el recurso.
- `404 Not Found`: recurso no encontrado.
- `422 Unprocessable Entity`: validación de negocio, por ejemplo contraseña repetida.
- `429 Too Many Requests`: límite de reintentos en recuperación de contraseña.
- `500 Internal Server Error`: fallo inesperado o generación de exportaciones.

---

## 1. Usuario

### 1.1 Registrar usuario
**Método y ruta:** `POST /api/usuarios/registro`  
**Autenticación:** No requiere JWT  
**Descripción:** Crea un nuevo usuario y devuelve el usuario creado junto con los códigos de recuperación asociados.

**Headers requeridos:**
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "nombre": "Juan Pérez",
  "correo": "juan@example.com",
  "clave": "MiClaveSegura123"
}
```

**Respuesta exitosa:** `201 Created`
```json
{
  "usuario": {
    "id": 1,
    "nombre": "Juan Pérez",
    "correo": "juan@example.com",
    "clave": "<hash>",
    "fechaRegistro": "2026-06-01T10:15:30",
    "estado": "ACTIVO"
  },
  "codigosRecuperacion": ["123456", "654321"]
}
```

**Posibles errores:**
- `400 Bad Request`: campos inválidos o faltantes.
- `409 Conflict`: correo ya registrado.
- `500 Internal Server Error`: fallo al persistir el usuario.

### 1.2 Iniciar sesión
**Método y ruta:** `POST /api/usuarios/login`  
**Autenticación:** No requiere JWT  
**Descripción:** Verifica credenciales y devuelve un JWT como texto plano.

**Headers requeridos:**
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "correo": "juan@example.com",
  "clave": "MiClaveSegura123"
}
```

**Respuesta exitosa:** `200 OK`  
**Tipo de respuesta:** texto plano con el token JWT.

```text
<jwt>
```

**Posibles errores:**
- `401 Unauthorized`: credenciales inválidas.
- `403 Forbidden`: usuario inactivo o bloqueado.
- `404 Not Found`: el correo no está registrado.

### 1.3 Cerrar sesión
**Método y ruta:** `POST /api/usuarios/logout`  
**Autenticación:** Requiere JWT  
**Descripción:** Respuesta de cierre de sesión; el backend no invalida el token, así que el frontend debe eliminarlo localmente.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`
```text
Sesion cerrada exitosamente.
```

**Posibles errores:**
- No hay errores de negocio definidos en el controlador.

### 1.4 Recuperar contraseña
**Método y ruta:** `POST /api/usuarios/recover`  
**Autenticación:** No requiere JWT  
**Descripción:** Valida el código de recuperación enviado por correo y devuelve un token temporal para resetear la contraseña.

**Headers requeridos:**
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "correo": "juan@example.com",
  "codigo": "123456"
}
```

**Respuesta exitosa:** `200 OK`  
**Tipo de respuesta:** texto plano con el token temporal.

```text
<token-temporal>
```

**Posibles errores:**
- `400 Bad Request`: datos faltantes o inválidos.
- `404 Not Found`: correo o código no válido.
- `429 Too Many Requests`: demasiados intentos de recuperación.

### 1.5 Restablecer contraseña
**Método y ruta:** `POST /api/usuarios/reset-password`  
**Autenticación:** No requiere JWT  
**Descripción:** Cambia la contraseña usando el token temporal obtenido en el paso anterior.

**Headers requeridos:**
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "tokenTemporal": "<token-temporal>",
  "nuevaClave": "NuevaClaveSegura123"
}
```

**Respuesta exitosa:** `200 OK`
```text
Contraseña actualizada exitosamente.
```

**Posibles errores:**
- `400 Bad Request`: token temporal inválido o datos incompletos.
- `404 Not Found`: token temporal no encontrado o expirado.
- `401 Unauthorized`: token temporal inválido, según la validación interna.

### 1.6 Actualizar contraseña actual
**Método y ruta:** `PUT /api/usuarios/actualizarClave`  
**Autenticación:** Requiere JWT  
**Descripción:** Permite cambiar la contraseña del usuario autenticado validando la clave antigua.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "claveAntigua": "MiClaveActual123",
  "claveNueva": "MiClaveNueva123"
}
```

**Respuesta exitosa:** `200 OK`  
**Tipo de respuesta:** texto plano con confirmación.

```text
Contraseña actualizada correctamente.
```

**Posibles errores:**
- `400 Bad Request`: campos vacíos o faltantes.
- `401 Unauthorized`: contraseña actual incorrecta.
- `404 Not Found`: usuario autenticado no encontrado.
- `422 Unprocessable Entity`: la nueva contraseña no puede ser igual a la anterior.

---

## 2. Categorías

### 2.1 Crear categoría propia
**Método y ruta:** `POST /api/categorias/crearCategoriaPropia`  
**Autenticación:** Requiere JWT  
**Descripción:** Crea una categoría personalizada para el usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "nombre": "Transporte",
  "descripcion": "Gastos de transporte y movilidad"
}
```

**Respuesta exitosa:** `201 Created`
```json
{
  "id": 10,
  "nombre": "Transporte",
  "descripcion": "Gastos de transporte y movilidad",
  "usuario": {
    "id": 1,
    "nombre": "Juan Pérez",
    "correo": "juan@example.com",
    "clave": "<hash>",
    "fechaRegistro": "2026-06-01T10:15:30",
    "estado": "ACTIVO"
  }
}
```

**Posibles errores:**
- `400 Bad Request`: nombre o descripción faltantes.
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: error al persistir la categoría.

### 2.2 Listar categorías del usuario
**Método y ruta:** `GET /api/categorias/usuario`  
**Autenticación:** Requiere JWT  
**Descripción:** Devuelve las categorías visibles para el usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`
```json
[
  {
    "id": 1,
    "nombre": "Alimentación",
    "descripcion": "Compras de alimentos",
    "usuario": null
  },
  {
    "id": 10,
    "nombre": "Transporte",
    "descripcion": "Gastos de transporte y movilidad",
    "usuario": {
      "id": 1,
      "nombre": "Juan Pérez",
      "correo": "juan@example.com",
      "clave": "<hash>",
      "fechaRegistro": "2026-06-01T10:15:30",
      "estado": "ACTIVO"
    }
  }
]
```

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.
- `404 Not Found`: usuario autenticado no encontrado.

### 2.3 Actualizar categoría propia
**Método y ruta:** `PUT /api/categorias/actualizarCategoriaPropia/{idCategoria}`  
**Autenticación:** Requiere JWT  
**Descripción:** Actualiza una categoría personalizada identificada por `idCategoria`.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "nombre": "Transporte",
  "descripcion": "Movilidad, taxi y bus"
}
```

**Respuesta exitosa:** `200 OK`
```json
{
  "id": 10,
  "nombre": "Transporte",
  "descripcion": "Movilidad, taxi y bus",
  "usuario": {
    "id": 1,
    "nombre": "Juan Pérez",
    "correo": "juan@example.com",
    "clave": "<hash>",
    "fechaRegistro": "2026-06-01T10:15:30",
    "estado": "ACTIVO"
  }
}
```

**Posibles errores:**
- `400 Bad Request`: cuerpo inválido.
- `401 Unauthorized`: token ausente o inválido.
- `403 Forbidden`: no se puede actualizar una categoría global o no hay permiso.
- `404 Not Found`: categoría no encontrada.

### 2.4 Eliminar categoría propia
**Método y ruta:** `DELETE /api/categorias/eliminarCategoriaPropia/{idCategoria}`  
**Autenticación:** Requiere JWT  
**Descripción:** Elimina una categoría personalizada del usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `204 No Content`

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.
- `403 Forbidden`: no se puede eliminar una categoría global o no hay permiso.
- `404 Not Found`: categoría no encontrada.

---

## 3. Transacciones

### 3.1 Crear transacción
**Método y ruta:** `POST /api/transacciones`  
**Autenticación:** Requiere JWT  
**Descripción:** Crea una transacción de ingreso o egreso para el usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "idCategoria": 10,
  "tipo": "EGRESO",
  "descripcion": "Pago de transporte",
  "monto": "12000"
}
```

**Respuesta exitosa:** `201 Created`
```json
{
  "transaccion": {
    "id": 100,
    "usuario": {
      "id": 1,
      "nombre": "Juan Pérez",
      "correo": "juan@example.com",
      "clave": "<hash>",
      "fechaRegistro": "2026-06-01T10:15:30",
      "estado": "ACTIVO"
    },
    "categoria": {
      "id": 10,
      "nombre": "Transporte",
      "descripcion": "Movilidad, taxi y bus",
      "usuario": {
        "id": 1,
        "nombre": "Juan Pérez",
        "correo": "juan@example.com",
        "clave": "<hash>",
        "fechaRegistro": "2026-06-01T10:15:30",
        "estado": "ACTIVO"
      }
    },
    "tipo": "EGRESO",
    "monto": 12000,
    "fecha": "2026-06-01T11:00:00",
    "descripcion": "Pago de transporte"
  },
  "alertasGeneradas": []
}
```

**Posibles errores:**
- `400 Bad Request`: monto inválido, tipo faltante o usuario no autenticado en el contexto del servicio.
- `401 Unauthorized`: token ausente o inválido.
- `403 Forbidden`: no tiene permiso sobre la categoría.
- `404 Not Found`: categoría no encontrada.
- `500 Internal Server Error`: error al crear la transacción.

### 3.2 Listar transacciones del usuario
**Método y ruta:** `GET /api/transacciones/usuario`  
**Autenticación:** Requiere JWT  
**Descripción:** Devuelve todas las transacciones del usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`
```json
[
  {
    "id": 100,
    "usuario": {
      "id": 1,
      "nombre": "Juan Pérez",
      "correo": "juan@example.com",
      "clave": "<hash>",
      "fechaRegistro": "2026-06-01T10:15:30",
      "estado": "ACTIVO"
    },
    "categoria": {
      "id": 10,
      "nombre": "Transporte",
      "descripcion": "Movilidad, taxi y bus",
      "usuario": {
        "id": 1,
        "nombre": "Juan Pérez",
        "correo": "juan@example.com",
        "clave": "<hash>",
        "fechaRegistro": "2026-06-01T10:15:30",
        "estado": "ACTIVO"
      }
    },
    "tipo": "EGRESO",
    "monto": 12000,
    "fecha": "2026-06-01T11:00:00",
    "descripcion": "Pago de transporte"
  }
]
```

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.
- `400 Bad Request`: no hay usuario autenticado.

### 3.3 Listar ingresos recientes
**Método y ruta:** `GET /api/transacciones/usuario/ingresos`  
**Autenticación:** Requiere JWT  
**Descripción:** Devuelve las transacciones de tipo ingreso del usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`
```json
[
  {
    "id": 101,
    "usuario": {
      "id": 1,
      "nombre": "Juan Pérez",
      "correo": "juan@example.com",
      "clave": "<hash>",
      "fechaRegistro": "2026-06-01T10:15:30",
      "estado": "ACTIVO"
    },
    "categoria": {
      "id": 2,
      "nombre": "Salario",
      "descripcion": "Ingreso por nómina",
      "usuario": null
    },
    "tipo": "INGRESO",
    "monto": 2500000,
    "fecha": "2026-06-01T08:00:00",
    "descripcion": "Pago mensual"
  }
]
```

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.
- `400 Bad Request`: no hay usuario autenticado.

### 3.4 Listar gastos recientes
**Método y ruta:** `GET /api/transacciones/usuario/gastos`  
**Autenticación:** Requiere JWT  
**Descripción:** Devuelve las transacciones de tipo gasto del usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`
```json
[
  {
    "id": 102,
    "usuario": {
      "id": 1,
      "nombre": "Juan Pérez",
      "correo": "juan@example.com",
      "clave": "<hash>",
      "fechaRegistro": "2026-06-01T10:15:30",
      "estado": "ACTIVO"
    },
    "categoria": {
      "id": 10,
      "nombre": "Transporte",
      "descripcion": "Movilidad, taxi y bus",
      "usuario": {
        "id": 1,
        "nombre": "Juan Pérez",
        "correo": "juan@example.com",
        "clave": "<hash>",
        "fechaRegistro": "2026-06-01T10:15:30",
        "estado": "ACTIVO"
      }
    },
    "tipo": "EGRESO",
    "monto": 12000,
    "fecha": "2026-06-01T11:00:00",
    "descripcion": "Pago de transporte"
  }
]
```

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.
- `400 Bad Request`: no hay usuario autenticado.

### 3.5 Actualizar transacción
**Método y ruta:** `PUT /api/transacciones/{idTransaccion}/usuario`  
**Autenticación:** Requiere JWT  
**Descripción:** Actualiza una transacción del usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "idCategoria": 10,
  "tipo": "EGRESO",
  "descripcion": "Pago de transporte urbano",
  "monto": "15000"
}
```

**Respuesta exitosa:** `200 OK`
```json
{
  "id": 100,
  "usuario": {
    "id": 1,
    "nombre": "Juan Pérez",
    "correo": "juan@example.com",
    "clave": "<hash>",
    "fechaRegistro": "2026-06-01T10:15:30",
    "estado": "ACTIVO"
  },
  "categoria": {
    "id": 10,
    "nombre": "Transporte",
    "descripcion": "Movilidad, taxi y bus",
    "usuario": {
      "id": 1,
      "nombre": "Juan Pérez",
      "correo": "juan@example.com",
      "clave": "<hash>",
      "fechaRegistro": "2026-06-01T10:15:30",
      "estado": "ACTIVO"
    }
  },
  "tipo": "EGRESO",
  "monto": 15000,
  "fecha": "2026-06-01T11:00:00",
  "descripcion": "Pago de transporte urbano"
}
```

**Posibles errores:**
- `400 Bad Request`: datos inválidos o monto mal formado.
- `401 Unauthorized`: token ausente o inválido.
- `403 Forbidden`: no hay permiso sobre la categoría.
- `404 Not Found`: transacción o categoría no encontrada.

### 3.6 Eliminar transacción
**Método y ruta:** `DELETE /api/transacciones/{idTransaccion}/usuario`  
**Autenticación:** Requiere JWT  
**Descripción:** Elimina una transacción del usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `204 No Content`

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.
- `400 Bad Request`: usuario no autenticado.
- `404 Not Found`: transacción no encontrada para ese usuario.

---

## 4. Transacciones programadas

### 4.1 Crear transacción programada
**Método y ruta:** `POST /api/transacciones-programadas/usuario`  
**Autenticación:** Requiere JWT  
**Descripción:** Crea una programación de ingresos o egresos recurrentes.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "monto": "500000",
  "descripcion": "Abono mensual del arriendo",
  "fechaInicio": "2026-06-05",
  "fechaFin": "2026-12-05",
  "frecuencia": "MENSUAL",
  "tipo": "EGRESO",
  "idCategoria": 10
}
```

**Respuesta exitosa:** `201 Created`
```json
{
  "id": 200,
  "usuario": {
    "id": 1,
    "nombre": "Juan Pérez",
    "correo": "juan@example.com",
    "clave": "<hash>",
    "fechaRegistro": "2026-06-01T10:15:30",
    "estado": "ACTIVO"
  },
  "categoria": {
    "id": 10,
    "nombre": "Transporte",
    "descripcion": "Movilidad, taxi y bus",
    "usuario": {
      "id": 1,
      "nombre": "Juan Pérez",
      "correo": "juan@example.com",
      "clave": "<hash>",
      "fechaRegistro": "2026-06-01T10:15:30",
      "estado": "ACTIVO"
    }
  },
  "tipo": "EGRESO",
  "frecuencia": "MENSUAL",
  "estado": "ACTIVO",
  "monto": 500000,
  "descripcion": "Abono mensual del arriendo",
  "fechaInicio": "2026-06-05",
  "fechaFin": "2026-12-05",
  "ultimaEjecucion": null
}
```

**Posibles errores:**
- `400 Bad Request`: tipo, frecuencia, fecha o categoría inválidos.
- `401 Unauthorized`: token ausente o inválido.
- `403 Forbidden`: no hay permiso sobre la categoría.
- `404 Not Found`: categoría no encontrada.

### 4.2 Actualizar transacción programada
**Método y ruta:** `PUT /api/transacciones-programadas/usuario/{id}`  
**Autenticación:** Requiere JWT  
**Descripción:** Modifica una programación existente; no permite cambiar `tipo` ni `idCategoria`.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "monto": "550000",
  "descripcion": "Arriendo mensual actualizado",
  "fechaInicio": "2026-06-05",
  "fechaFin": "2026-12-05",
  "frecuencia": "MENSUAL",
  "estado": "ACTIVO"
}
```

**Respuesta exitosa:** `200 OK`
```json
{
  "id": 200,
  "usuario": {
    "id": 1,
    "nombre": "Juan Pérez",
    "correo": "juan@example.com",
    "clave": "<hash>",
    "fechaRegistro": "2026-06-01T10:15:30",
    "estado": "ACTIVO"
  },
  "categoria": {
    "id": 10,
    "nombre": "Transporte",
    "descripcion": "Movilidad, taxi y bus",
    "usuario": {
      "id": 1,
      "nombre": "Juan Pérez",
      "correo": "juan@example.com",
      "clave": "<hash>",
      "fechaRegistro": "2026-06-01T10:15:30",
      "estado": "ACTIVO"
    }
  },
  "tipo": "EGRESO",
  "frecuencia": "MENSUAL",
  "estado": "ACTIVO",
  "monto": 550000,
  "descripcion": "Arriendo mensual actualizado",
  "fechaInicio": "2026-06-05",
  "fechaFin": "2026-12-05",
  "ultimaEjecucion": null
}
```

**Posibles errores:**
- `400 Bad Request`: monto, frecuencia o fecha inválidos.
- `401 Unauthorized`: token ausente o inválido.
- `403 Forbidden`: no hay permiso para usar esa categoría.
- `404 Not Found`: programación no encontrada.

### 4.3 Eliminar transacción programada
**Método y ruta:** `DELETE /api/transacciones-programadas/usuario/{id}`  
**Autenticación:** Requiere JWT  
**Descripción:** Elimina una programación recurrente.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `204 No Content`

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.
- `404 Not Found`: programación no encontrada.

### 4.4 Listar ingresos programados
**Método y ruta:** `GET /api/transacciones-programadas/usuario/ingresosProgramados`  
**Autenticación:** Requiere JWT  
**Descripción:** Lista las programaciones de ingresos del usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`
```json
[
  {
    "id": 201,
    "tipo": "INGRESO",
    "frecuencia": "MENSUAL",
    "estado": "ACTIVO",
    "monto": 2500000,
    "descripcion": "Salario",
    "fechaInicio": "2026-06-01",
    "fechaFin": null,
    "ultimaEjecucion": "2026-06-01T08:00:00"
  }
]
```

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.

### 4.5 Listar egresos programados
**Método y ruta:** `GET /api/transacciones-programadas/usuario/egresosProgramados`  
**Autenticación:** Requiere JWT  
**Descripción:** Lista las programaciones de egresos del usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`
```json
[
  {
    "id": 202,
    "tipo": "EGRESO",
    "frecuencia": "MENSUAL",
    "estado": "ACTIVO",
    "monto": 500000,
    "descripcion": "Arriendo",
    "fechaInicio": "2026-06-05",
    "fechaFin": "2026-12-05",
    "ultimaEjecucion": null
  }
]
```

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.

---

## 5. Presupuestos

### 5.1 Crear presupuesto global
**Método y ruta:** `POST /api/presupuestos/global`  
**Autenticación:** Requiere JWT  
**Descripción:** Crea un presupuesto global para el usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "montoLimite": 2500000
}
```

**Respuesta exitosa:** `201 Created`
```json
{
  "id": 300,
  "usuario": {
    "id": 1,
    "nombre": "Juan Pérez",
    "correo": "juan@example.com",
    "clave": "<hash>",
    "fechaRegistro": "2026-06-01T10:15:30",
    "estado": "ACTIVO"
  },
  "categoria": null,
  "montoLimite": 2500000,
  "fechaLimite": "2026-06-30T23:59:59"
}
```

**Posibles errores:**
- `400 Bad Request`: monto inválido o faltante.
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: error al crear el presupuesto.

### 5.2 Crear presupuesto por categoría
**Método y ruta:** `POST /api/presupuestos/categoria`  
**Autenticación:** Requiere JWT  
**Descripción:** Crea un presupuesto asociado a una categoría específica.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`
- `Content-Type: application/json`

**Cuerpo del request:**
```json
{
  "idCategoria": 10,
  "montoLimite": 400000
}
```

**Respuesta exitosa:** `201 Created`
```json
{
  "id": 301,
  "usuario": {
    "id": 1,
    "nombre": "Juan Pérez",
    "correo": "juan@example.com",
    "clave": "<hash>",
    "fechaRegistro": "2026-06-01T10:15:30",
    "estado": "ACTIVO"
  },
  "categoria": {
    "id": 10,
    "nombre": "Transporte",
    "descripcion": "Movilidad, taxi y bus",
    "usuario": {
      "id": 1,
      "nombre": "Juan Pérez",
      "correo": "juan@example.com",
      "clave": "<hash>",
      "fechaRegistro": "2026-06-01T10:15:30",
      "estado": "ACTIVO"
    }
  },
  "montoLimite": 400000,
  "fechaLimite": "2026-06-30T23:59:59"
}
```

**Posibles errores:**
- `400 Bad Request`: categoría o monto inválidos.
- `401 Unauthorized`: token ausente o inválido.
- `403 Forbidden`: la categoría no pertenece al usuario.
- `404 Not Found`: categoría no encontrada.

### 5.3 Obtener resumen del presupuesto global
**Método y ruta:** `GET /api/presupuestos/global/usuario`  
**Autenticación:** Requiere JWT  
**Descripción:** Devuelve el estado actual del presupuesto global del usuario.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`
```json
{
  "presupuestoDefinido": true,
  "idPresupuesto": 300,
  "montoLimite": 2500000,
  "gastado": 1200000,
  "disponible": 1300000,
  "porcentajeUso": 48.0,
  "fechaLimite": "2026-06-30T23:59:59",
  "mensaje": null
}
```

Si no existe presupuesto global:
```json
{
  "presupuestoDefinido": false,
  "idPresupuesto": null,
  "montoLimite": null,
  "gastado": null,
  "disponible": null,
  "porcentajeUso": null,
  "fechaLimite": null,
  "mensaje": "No tienes un presupuesto global definido."
}
```

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.

### 5.4 Obtener resumen de presupuestos por categoría
**Método y ruta:** `GET /api/presupuestos/categorias/usuario`  
**Autenticación:** Requiere JWT  
**Descripción:** Devuelve el resumen de todos los presupuestos por categoría del usuario.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`
```json
[
  {
    "idPresupuesto": 301,
    "idCategoria": 10,
    "nombreCategoria": "Transporte",
    "montoLimite": 400000,
    "gastado": 120000,
    "disponible": 280000,
    "porcentajeUso": 30.0,
    "fechaLimite": "2026-06-30T23:59:59"
  }
]
```

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.

---

## 6. Reportes

### 6.1 Gastos por categoría
**Método y ruta:** `GET /api/reports/expenses`  
**Autenticación:** Requiere JWT  
**Descripción:** Devuelve el total de gastos por categoría para un mes y año dados. Si no se envían `month` o `year`, usa el mes y año actual.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Query params:**
- `month` opcional, default `0`
- `year` opcional, default `0`

**Ejemplo:** `/api/reports/expenses?month=6&year=2026`

**Respuesta exitosa:** `200 OK`
```json
[
  {
    "idCategoria": 10,
    "nombreCategoria": "Transporte",
    "totalGastado": 120000,
    "cantidadTransacciones": 8
  }
]
```

**Posibles errores:**
- `400 Bad Request`: mes fuera de rango o año inválido.
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: fallo inesperado del servicio.

### 6.2 Ingresos por categoría
**Método y ruta:** `GET /api/reports/income`  
**Autenticación:** Requiere JWT  
**Descripción:** Devuelve el total de ingresos por categoría para un mes y año dados.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Query params:**
- `month` opcional, default `0`
- `year` opcional, default `0`

**Respuesta exitosa:** `200 OK`
```json
[
  {
    "idCategoria": 2,
    "nombreCategoria": "Salario",
    "totalIngresado": 2500000,
    "cantidadTransacciones": 1
  }
]
```

**Posibles errores:**
- `400 Bad Request`: mes fuera de rango o año inválido.
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: fallo inesperado del servicio.

### 6.3 Exportar gastos por categoría
**Método y ruta:** `GET /api/reports/expenses/export`  
**Autenticación:** Requiere JWT  
**Descripción:** Exporta los gastos por categoría en PDF o CSV.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Query params obligatorios:**
- `type`: `csv` o `pdf`

**Query params opcionales:**
- `month`, default `0`
- `year`, default `0`

**Ejemplo:** `/api/reports/expenses/export?type=csv&month=6&year=2026`

**Respuesta exitosa:** `200 OK`  
**Tipo de respuesta:** binaria.  
**Headers relevantes:**
- `Content-Type: text/csv` o `application/pdf`
- `Content-Disposition: attachment; filename="..."`

**Posibles errores:**
- `400 Bad Request`: `type` no válido.
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: error al generar el archivo.

### 6.4 Exportar ingresos por categoría
**Método y ruta:** `GET /api/reports/income/export`  
**Autenticación:** Requiere JWT  
**Descripción:** Exporta los ingresos por categoría en PDF o CSV.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Query params obligatorios:**
- `type`: `csv` o `pdf`

**Query params opcionales:**
- `month`, default `0`
- `year`, default `0`

**Respuesta exitosa:** `200 OK`  
**Tipo de respuesta:** binaria con descarga.

**Posibles errores:**
- `400 Bad Request`: `type` no válido.
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: error al generar el archivo.

### 6.5 Resumen mensual
**Método y ruta:** `GET /api/reports/summary`  
**Autenticación:** Requiere JWT  
**Descripción:** Devuelve un resumen financiero mensual con ingresos, egresos, balance y porcentaje de ahorro.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Query params:**
- `month` opcional, default `0`
- `year` opcional, default `0`

**Respuesta exitosa:** `200 OK`
```json
{
  "totalIngresos": 2500000,
  "totalEgresos": 1800000,
  "balance": 700000,
  "porcentajeAhorro": 28.0,
  "mes": 6,
  "anio": 2026
}
```

**Posibles errores:**
- `400 Bad Request`: mes o año inválido.
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: fallo inesperado del servicio.

### 6.6 Exportar resumen mensual
**Método y ruta:** `GET /api/reports/export`  
**Autenticación:** Requiere JWT  
**Descripción:** Exporta el resumen mensual en PDF o CSV.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Query params obligatorios:**
- `type`: `csv` o `pdf`

**Query params opcionales:**
- `month`, default `0`
- `year`, default `0`

**Respuesta exitosa:** `200 OK`  
**Tipo de respuesta:** binaria con descarga.

**Posibles errores:**
- `400 Bad Request`: `type` no válido.
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: error al generar el archivo.

### 6.7 Comparativo mensual
**Método y ruta:** `GET /api/reports/monthly-comparison`  
**Autenticación:** Requiere JWT  
**Descripción:** Devuelve una comparativa del mes con ingresos, gastos, balance, estado y datos para gráficos.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Query params:**
- `month` opcional, default `0`
- `year` opcional, default `0`

**Respuesta exitosa:** `200 OK`
```json
{
  "nombreUsuario": "Juan Pérez",
  "mes": 6,
  "anio": 2026,
  "totalIngresos": 2500000,
  "totalGastos": 1800000,
  "balance": 700000,
  "estadoBalance": "Superávit",
  "montoDeficit": 0,
  "porcentajeAhorro": 28.0,
  "datosGrafico": {
    "ingresos": 2500000,
    "gastos": 1800000
  },
  "movimientosResumen": [
    {
      "fecha": "2026-06-01T08:00:00",
      "tipo": "INGRESO",
      "categoria": "Salario",
      "monto": 2500000,
      "descripcion": "Pago mensual"
    }
  ]
}
```

**Posibles errores:**
- `400 Bad Request`: mes o año inválido.
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: fallo inesperado del servicio.

### 6.8 Exportar comparativo mensual
**Método y ruta:** `GET /api/reports/monthly-comparison/export`  
**Autenticación:** Requiere JWT  
**Descripción:** Exporta la comparativa mensual en PDF o CSV.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Query params obligatorios:**
- `type`: `csv` o `pdf`

**Query params opcionales:**
- `month`, default `0`
- `year`, default `0`

**Respuesta exitosa:** `200 OK`  
**Tipo de respuesta:** binaria con descarga.

**Posibles errores:**
- `400 Bad Request`: `type` no válido.
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: error al generar el archivo.

---

## 7. Alertas

### 7.1 Historial de alertas del usuario
**Método y ruta:** `GET /api/alertas/usuario`  
**Autenticación:** Requiere JWT  
**Descripción:** Devuelve el historial de alertas del usuario autenticado.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`
```json
[
  {
    "id": 500,
    "presupuestoId": 300,
    "tipo": "PROXIMIDAD",
    "mensaje": "Te acercas al límite de tu presupuesto global",
    "fecha": "2026-06-01T12:00:00"
  }
]
```

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.
- `400 Bad Request`: usuario no autenticado en el servicio.

---

## 8. Recomendaciones

### 8.1 Recomendaciones por balance
**Método y ruta:** `GET /api/recomendaciones/balance`  
**Autenticación:** Requiere JWT  
**Descripción:** Genera recomendaciones personalizadas según el balance mensual.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`  
**Tipo de respuesta:** texto plano.

```text
Recomendación textual generada por el servicio.
```

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: fallo al generar el texto.

### 8.2 Recomendaciones por alertas
**Método y ruta:** `GET /api/recomendaciones/alertas`  
**Autenticación:** Requiere JWT  
**Descripción:** Genera recomendaciones a partir de alertas y presupuestos.

**Headers requeridos:**
- `Authorization: Bearer <jwt>`

**Cuerpo del request:** No aplica.

**Respuesta exitosa:** `200 OK`  
**Tipo de respuesta:** texto plano.

```text
Recomendación textual generada por el servicio.
```

**Posibles errores:**
- `401 Unauthorized`: token ausente o inválido.
- `500 Internal Server Error`: fallo al generar el texto.

---

## 9. Resumen rápido para frontend

### Rutas públicas
- `POST /api/usuarios/registro`
- `POST /api/usuarios/login`
- `POST /api/usuarios/recover`
- `POST /api/usuarios/reset-password`

### Rutas protegidas con JWT
- Todas las demás rutas bajo `/api/**`

### Tipos de respuesta importantes
- JSON con objetos/colecciones para CRUD y reportes.
- Texto plano para `login`, `logout`, `recover`, `reset-password`, `actualizarClave` y recomendaciones.
- Binario descargable para los endpoints `/export`.

### Encabezados recomendados en el frontend
- `Authorization: Bearer <jwt>` en rutas protegidas.
- `Content-Type: application/json` en cualquier request con cuerpo JSON.
- Manejar `Content-Disposition` para descargas de archivos.
