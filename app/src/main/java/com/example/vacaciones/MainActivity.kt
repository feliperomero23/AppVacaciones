package com.example.vacaciones

import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDateTime

enum class Pantalla {
    FORM,
    FOTO,
    MAPA,
    IMAGEN_COMPLETA
}

class FormRecepcionViewModel : ViewModel() {
    val lugar = mutableStateOf("")
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)
    val fotolugares = mutableStateOf<List<Uri>>(emptyList())//Variable de tipo lista que nos almacenara las fotos


    //funcion para maximo de fotos
    private fun maxPhotosReached(): Boolean {
        return fotolugares.value.size >= maxPhotos
    }

    // Definir el límite máximo de fotos
    private val maxPhotos = 4
    val mensajeError = mutableStateOf<String?>(null)

    fun agregarFoto(uri: Uri) {
        // Verificar si ya se alcanzó el límite maximo de fotos
        if (fotolugares.value.size < maxPhotos) {
            fotolugares.value = fotolugares.value + uri
        } else {
            // Establecer el mensaje de error si se alcanza el límite máximo
            mensajeError.value = ""
        }
    }
}

class CameraAppViewModel : ViewModel() {
    val pantalla = mutableStateOf(Pantalla.FORM)
    var imagenEnPantallaCompleta: Uri? by mutableStateOf(null)

    // callbacks
    var onPermisoCamaraOk: () -> Unit = {}
    var onPermisoUbicacionOk: () -> Unit = {}

    // lanzador permisos
    var lanzadorPermisos: ActivityResultLauncher<Array<String>>? = null

    fun cambiarPantallaFoto() {
        pantalla.value = Pantalla.FOTO
    }

    fun cambiarPantallaForm() {
        pantalla.value = Pantalla.FORM
    }

    fun cambiarPantallaMapa() {
        pantalla.value = Pantalla.MAPA
    }

    fun mostrarImagenCompleta(uri: Uri) {
        imagenEnPantallaCompleta = uri
        pantalla.value = Pantalla.IMAGEN_COMPLETA
    }

    fun cerrarImagenCompleta() {
        imagenEnPantallaCompleta = null
        pantalla.value = Pantalla.FORM
    }
}
//Manejo solicitud de permisos camara y ubicacion.
class MainActivity : ComponentActivity() {
    val cameraAppVm: CameraAppViewModel by viewModels()

    lateinit var cameraController: LifecycleCameraController
    val lanzadorPermisos =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            when {
                (it[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false)
                        or (it[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso ubicacion granted")
                    cameraAppVm.onPermisoUbicacionOk()
                }

                (it[android.Manifest.permission.CAMERA] ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso camara granted")
                    cameraAppVm.onPermisoCamaraOk()
                }

                else -> {
                }
            }
        }

    private fun setupCamara() {
        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraAppVm.lanzadorPermisos = lanzadorPermisos
        setupCamara()
        setContent {
            AppUI(cameraController)
        }
    }
}

fun generarNombreSegunFechaHastaSegundo(): String = LocalDateTime
    .now().toString().replace(Regex(" [T:.-]"), "").substring(0, 14)


fun crearArchivoImagenPublico(contexto: Context): File = File(
    contexto.getExternalFilesDir(
        Environment.DIRECTORY_PICTURES
    ),
    "${generarNombreSegunFechaHastaSegundo()}.jpg"
)

fun uri2imageBitmap(uri: Uri, contexto: Context) =
    BitmapFactory.decodeStream(contexto.contentResolver.openInputStream(uri)).asImageBitmap()
//Funcion para la toma de fotografias
fun tomarFotografia(
    cameraController: CameraController,
    archivo: File,
    contexto: Context,
    imagenGuardadaOk: (uri: Uri) -> Unit
) {
    val outputFileOptions = ImageCapture.OutputFileOptions.Builder(archivo).build()

    cameraController.takePicture(
        outputFileOptions, ContextCompat.getMainExecutor(contexto),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.also { uri ->
                    Log.v(
                        "tomarFotografia()::onImageSaved",
                        "Foto guardada en ${uri.toString()}"
                    )
                    imagenGuardadaOk(uri)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("tomarFotografia()", "Error: ${exception.message}")
            }
        }
    )
}

class SinPermisoException(mensaje: String) : Exception(mensaje)

fun getUbicacion(contexto: Context, onUbicacionOk: (location: Location) -> Unit) {
    try {
        val servicio = LocationServices.getFusedLocationProviderClient(contexto)
        val tarea = servicio.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        tarea.addOnSuccessListener {
            onUbicacionOk(it)
        }
    } catch (e: SecurityException) {
        throw SinPermisoException(e.message ?: "No tiene permisos para conseguir la ubicación")
    }
}

@Composable
fun AppUI(cameraController: CameraController) {
    val contexto = LocalContext.current
    val cameraAppViewModel: CameraAppViewModel = viewModel()
    val formRecepcionVm: FormRecepcionViewModel = viewModel()

    when (cameraAppViewModel.pantalla.value) {
        Pantalla.FORM -> {
            PantallaFormUI(
                formRecepcionVm,
                tomarFotoOnClick = {
                    cameraAppViewModel.cambiarPantallaFoto()
                    cameraAppViewModel.lanzadorPermisos?.launch(
                        arrayOf(
                            android.Manifest.permission.CAMERA
                        )
                    )
                },
                actualizarUbicacionOnClick = {
                    cameraAppViewModel.onPermisoUbicacionOk = {
                        getUbicacion(contexto) {
                            formRecepcionVm.latitud.value = it.latitude
                            formRecepcionVm.longitud.value = it.longitude
                        }
                    }
                    cameraAppViewModel.lanzadorPermisos?.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                abrirMapaOnClick = {
                    // Cambiar a la pantalla de mapa
                    cameraAppViewModel.cambiarPantallaMapa()
                }
            )
        }

        Pantalla.FOTO -> {
            PantallaFotoUI(
                formRecepcionVm, cameraAppViewModel,
                cameraController
            )
        }

        Pantalla.MAPA -> {
            PantallaMapaUI(
                formRecepcionVm.latitud.value,
                formRecepcionVm.longitud.value,
                volverOnClick = {
                    // Volver a la pantalla anterior
                    cameraAppViewModel.cambiarPantallaForm()
                }
            )
        }

        Pantalla.IMAGEN_COMPLETA -> {
            ImagenCompletaUI(
                cameraAppViewModel.imagenEnPantallaCompleta ?: Uri.EMPTY,
                cameraAppViewModel::cerrarImagenCompleta
            )
        }

        else -> {}
    }
}
// Pantalla formulario principal
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaFormUI(

    formRecepcionVm: FormRecepcionViewModel,
    tomarFotoOnClick: () -> Unit = {},
    actualizarUbicacionOnClick: () -> Unit = {},
    abrirMapaOnClick: () -> Unit = {}


) {
    val cameraAppViewModel: CameraAppViewModel = viewModel()
    val contexto = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(30.dp))
        TextField(
            label = { Text("Escribe lugar visitado") },
            value = formRecepcionVm.lugar.value,
            onValueChange = { formRecepcionVm.lugar.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        )

        // para mostrar las fotos en grupos de dos en una fila horizontal y luego hacia abajo

        val fotos = formRecepcionVm.fotolugares.value.chunked(2)
        fotos.forEach { grupo ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                grupo.forEach { imagenUri ->
                    Box(
                        modifier = Modifier
                            .size(200.dp, 100.dp)
                            .padding(10.dp)
                            .clickable {

                                // Mostrar la imagen en pantalla completa al hacer clic
                                cameraAppViewModel.mostrarImagenCompleta(imagenUri)
                            }
                    ) {
                        Image(
                            painter = BitmapPainter(
                                uri2imageBitmap(
                                    imagenUri,
                                    contexto
                                )
                            ),
                            contentDescription = "Imagen  ${formRecepcionVm.lugar.value}",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        Text("Pincha aquí para capturar:")
        Button(onClick = {
            tomarFotoOnClick()
        }) {
            Text("Tomar Fotografía")
        }

        //Comprobacion la cual muestra un mensaje en caso que ya se capturaron las 4 fotos permitidas
        if (formRecepcionVm.mensajeError.value != null) {

            // Mensaje de error
            Text(
                text = "Has alcanzado el límite máximo de fotos",
                color = Color.Black,
                modifier = Modifier.padding(5.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("La ubicación es: Latitud: ${formRecepcionVm.latitud.value} y Longitud: ${formRecepcionVm.longitud.value}")
        Button(onClick = {
            actualizarUbicacionOnClick()
        }) {
            Text("Actualizar Ubicación")
        }

        Button(
            onClick = {
                abrirMapaOnClick()
            }
        ) {
            Text("Abrir Mapa")
        }
    }
}





//Pantalla captura de foto .
@Composable
fun PantallaFotoUI(
    formRecepcionVm: FormRecepcionViewModel,
    appViewModel: CameraAppViewModel,
    cameraController: CameraController
) {
    val contexto = LocalContext.current
    AndroidView(
        factory = {
            PreviewView(it).apply { controller = cameraController }
        },
        modifier = Modifier.fillMaxSize()
    )
    if (formRecepcionVm.mensajeError.value != null) {
        Button(
            onClick = {
                appViewModel.cambiarPantallaForm()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Volver al formulario principal")
        }
    } else {
        // Si no hay mensaje de error , mostrar el botón para tomar la foto
        Button(
            onClick = {
                // Verificar nuevamente si hay un mensaje de error por cantidad de fotos
                if (formRecepcionVm.mensajeError.value == null) {

                    // Solo tomar la foto si no hay errores
                    tomarFotografia(
                        cameraController, crearArchivoImagenPublico(contexto), contexto
                    ) { uri ->
                        formRecepcionVm.agregarFoto(uri)
                        appViewModel.cambiarPantallaForm()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth() // Asegura que el botón ocupe todo el ancho disponible.
                .padding(16.dp)
        ) {
            Text("Tomar foto")
        }
    }
}
//Pantalla para la vista del mapa y ubicacion.
    @Composable
    fun PantallaMapaUI(
        latitud: Double,
        longitud: Double,
        volverOnClick: () -> Unit = {}
    ) {
        val contexto = LocalContext.current

        AndroidView(
            factory = {
                MapView(it).also {
                    it.setTileSource(TileSourceFactory.MAPNIK)
                    Configuration.getInstance().userAgentValue =
                        contexto.packageName
                }
            }, update = {
                it.overlays.removeIf { true }
                it.invalidate()

                it.controller.setZoom(18.0)
                val geoPoint = GeoPoint(latitud, longitud)
                it.controller.animateTo(geoPoint)

                val marcador = Marker(it)
                marcador.position = geoPoint
                marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                it.overlays.add(marcador)
            }
        )

        Button(
            onClick = {
                volverOnClick()
            },
            modifier = Modifier
                .fillMaxWidth() // Ocupa todo el ancho disponible
                .padding(16.dp) // Agrega un espacio alrededor del botón


        ) {
            Text("Volver")
        }
    }
// Funcion para cuando se hace click en una imagen esta se muestre en pantalla completa
    @Composable
    fun ImagenCompletaUI(
        imagenUri: Uri,
        cerrarImagenOnClick: () -> Unit = {}
    ) {
        val contexto = LocalContext.current


        val imagenBitmap = uri2imageBitmap(imagenUri, contexto)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = BitmapPainter(
                    uri2imageBitmap(
                        imagenUri,
                        contexto,


                        )
                ),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                    }
            )


            // Boton que nos permite cerrar y volver al formulario porincipal
            Button(
                onClick = {
                    cerrarImagenOnClick()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter) // Botón en la parte inferior
                    .padding(16.dp)
            ) {
                Text("Cerrar Imagen")
            }
        }
    }








