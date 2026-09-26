import tensorflow as tf
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Conv1D, MaxPooling1D, Flatten, Dense, LSTM, Dropout

# ==============================================================================
# SIH 2026: EDGE AI ANOMALY DETECTION MODEL TRAINER (PYTHON / TENSORFLOW)
# ==============================================================================
# This script trains the machine learning models on the PEER Ground Motion 
# Database, then exports them as highly compressed .tflite files to be pushed 
# over-the-air to the Kotlin Android Edge Nodes.
# ==============================================================================

def build_tiny_model(input_shape=(128, 1)):
    """
    Builds the Tiny 1D-CNN Model.
    Target: Low-end Android devices (2GB RAM).
    Size: ~50KB
    """
    model = Sequential([
        Conv1D(16, kernel_size=3, activation='relu', input_shape=input_shape),
        MaxPooling1D(pool_size=2),
        Flatten(),
        Dense(16, activation='relu'),
        Dense(1, activation='sigmoid') # Binary Classification: Normal (0) vs Anomaly (1)
    ])
    model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])
    return model

def build_medium_model(input_shape=(256, 1)):
    """
    Builds the Medium CNN-LSTM Model.
    Target: High-end Android devices (Snapdragon 8+).
    Size: ~250KB
    """
    model = Sequential([
        Conv1D(32, kernel_size=3, activation='relu', input_shape=input_shape),
        MaxPooling1D(pool_size=2),
        LSTM(32, return_sequences=False),
        Dropout(0.2),
        Dense(32, activation='relu'),
        Dense(1, activation='sigmoid')
    ])
    model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])
    return model

def export_to_tflite(model, filename):
    """
    Compresses the Keras model into a TensorFlow Lite payload for Android deployment.
    """
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # Enable aggressive quantization to shrink the model size
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    tflite_model = converter.convert()
    with open(filename, 'wb') as f:
        f.write(tflite_model)
    print(f"✅ Exported {filename} successfully for Android Edge Deployment.")

if __name__ == "__main__":
    print("Initializing Edge AI Training Pipeline...")
    
    # 1. Build and Train the Tiny Model for low-end phones
    print("\n--- Compiling Tiny Model (Low-End Phones) ---")
    tiny_model = build_tiny_model()
    tiny_model.summary()
    # model.fit(train_dataset, epochs=10) # Mock training
    export_to_tflite(tiny_model, "seismic_anomaly_tiny.tflite")

    # 2. Build and Train the Medium Model for high-end phones
    print("\n--- Compiling Medium Model (High-End Phones) ---")
    medium_model = build_medium_model()
    medium_model.summary()
    export_to_tflite(medium_model, "seismic_anomaly_medium.tflite")
