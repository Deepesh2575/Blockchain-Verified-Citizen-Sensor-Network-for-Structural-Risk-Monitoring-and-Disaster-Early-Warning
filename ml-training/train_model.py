import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models
import os

print("Initializing SIH 2026 Machine Learning Pipeline...")

# --- Parameters ---
NUM_SAMPLES = 10000
TIME_STEPS = 150 # 3 seconds at 50Hz
NUM_FEATURES = 3 # X, Y, Z axis accelerometer

def generate_synthetic_data(num_samples):
    print(f"Generating {num_samples} synthetic accelerometer sequences...")
    X = np.zeros((num_samples, TIME_STEPS, NUM_FEATURES))
    y = np.zeros(num_samples)
    
    for i in range(num_samples):
        # Base noise (e.g. phone sitting on a desk)
        noise_x = np.random.normal(0, 0.1, TIME_STEPS)
        noise_y = np.random.normal(0, 0.1, TIME_STEPS)
        noise_z = np.random.normal(9.8, 0.1, TIME_STEPS) # Gravity on Z
        
        # 50% chance to inject an "Anomaly" (Earthquake/Structural sway)
        if np.random.rand() > 0.5:
            # Inject a low-frequency high-amplitude wave (1 to 5 Hz)
            freq = np.random.uniform(1.0, 5.0)
            t = np.linspace(0, 3, TIME_STEPS)
            
            # P-wave and S-wave simulation
            sway_x = np.sin(2 * np.pi * freq * t) * np.random.uniform(2.0, 5.0)
            sway_y = np.cos(2 * np.pi * freq * t) * np.random.uniform(2.0, 5.0)
            
            X[i, :, 0] = noise_x + sway_x
            X[i, :, 1] = noise_y + sway_y
            X[i, :, 2] = noise_z
            y[i] = 1.0 # Label as Anomaly
        else:
            X[i, :, 0] = noise_x
            X[i, :, 1] = noise_y
            X[i, :, 2] = noise_z
            y[i] = 0.0 # Label as Normal
            
    return X, y

# Generate training and validation data
X_train, y_train = generate_synthetic_data(8000)
X_val, y_val = generate_synthetic_data(2000)

print("Building 1D-CNN Architecture...")
model = models.Sequential([
    layers.Input(shape=(TIME_STEPS, NUM_FEATURES)),
    layers.Conv1D(filters=16, kernel_size=5, activation='relu'),
    layers.MaxPooling1D(pool_size=2),
    layers.Conv1D(filters=32, kernel_size=3, activation='relu'),
    layers.MaxPooling1D(pool_size=2),
    layers.Flatten(),
    layers.Dense(32, activation='relu'),
    layers.Dropout(0.2),
    layers.Dense(1, activation='sigmoid') # Binary classification
])

model.compile(optimizer='adam',
              loss='binary_crossentropy',
              metrics=['accuracy'])

print("Training the model on synthesized data...")
model.fit(X_train, y_train, epochs=10, batch_size=32, validation_data=(X_val, y_val))

# Evaluate
loss, acc = model.evaluate(X_val, y_val)
print(f"Validation Accuracy: {acc * 100:.2f}%")

# Export to TFLite
print("Quantizing and exporting to TensorFlow Lite (.tflite)...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# Optimize for mobile
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

output_path = "anomaly_model.tflite"
with open(output_path, "wb") as f:
    f.write(tflite_model)

print(f"Success! Model saved as {output_path}")
print(f"Model Size: {os.path.getsize(output_path) / 1024:.2f} KB")
