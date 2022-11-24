from sklearn.preprocessing import OneHotEncoder
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import confusion_matrix
import seaborn as sns
from pandas.plotting import register_matplotlib_converters
from pylab import rcParams
import matplotlib.pyplot as plt
import argparse
from sklearn.metrics import classification_report
import os
    
def csv_to_dataframe(thingy_file, respeck_file):
    thingy = False
    respeck = False
    try:
        thingy_dataframe = pd.read_csv(thingy_file)
        thingy_dataframe = thingy_dataframe.rename(columns=
                    {'accel_x'     :'T_accel_x',
                    'accel_y'      :'T_accel_y',
                    'accel_z'      :'T_accel_z',
                    'gyro_x'       :'T_gyro_x' ,
                    'gyro_y'       :'T_gyro_y' ,
                    'gyro_z'       :'T_gyro_z' ,
                    'subject_id'   :'T_subject_id',
                    'activity_code':'T_activity_code',
                    'activity_type':'T_activity_type'})
        thingy = True
    except:
        print("Thingy file was not found.")
    try:
        respeck_dataframe = pd.read_csv(respeck_file)
        respeck_dataframe = respeck_dataframe.rename(columns=
                        {'accel_x'     :'R_accel_x',
                        'accel_y'      :'R_accel_y',
                        'accel_z'      :'R_accel_z',
                        'gyro_x'       :'R_gyro_x' ,
                        'gyro_y'       :'R_gyro_y' ,
                        'gyro_z'       :'R_gyro_z' ,
                        'subject_id'   :'R_subject_id',
                        'activity_code':'R_activity_code',
                        'activity_type':'R_activity_type'})
        respeck = True
    except:
        print("Respeck file was not found.")
    if respeck and thingy:
        # Respeck data and thingy data are not synchronized
        # Even with the method below, we can't 100% make sure that they are synchronized without checking at the data manually, which would take years to do
        thingy_dataframe = thingy_dataframe.sort_values(by=['T_subject_id', 'T_activity_code', 'timestamp']).reset_index(drop=True)
        thingy_dataframe = thingy_dataframe.loc[:, ['T_accel_x', 'T_accel_y', 'T_accel_z', 'T_gyro_x', 'T_gyro_y', 'T_gyro_z', 'mag_x', 'mag_y', 'mag_z', 
                                                'T_subject_id', 'T_activity_code', 'T_activity_type']]
        respeck_dataframe = respeck_dataframe.sort_values(by=['R_subject_id', 'R_activity_code', 'timestamp']).reset_index(drop=True)
        respeck_dataframe = respeck_dataframe.loc[:, ['R_accel_x', 'R_accel_y', 'R_accel_z', 'R_gyro_x', 'R_gyro_y', 'R_gyro_z', 'R_subject_id',
                                                'R_activity_code', 'R_activity_type']]
        i = 0
        j = 0
        lastid = thingy_dataframe['T_subject_id'].iloc[0]
        last = thingy_dataframe['T_activity_code'].iloc[0]
        drop_thingy = []
        drop_respeck = []
        while i < len(thingy_dataframe) and j < len(respeck_dataframe):
            if thingy_dataframe['T_activity_code'].iloc[i] != respeck_dataframe['R_activity_code'].iloc[j] \
                or thingy_dataframe['T_subject_id'].iloc[i] != respeck_dataframe['R_subject_id'].iloc[j]:
                if thingy_dataframe['T_activity_code'].iloc[i] != last or thingy_dataframe['T_subject_id'].iloc[i] != lastid:
                    drop_respeck.append(j)
                    j+=1
                if respeck_dataframe['R_activity_code'].iloc[j] != last or respeck_dataframe['R_subject_id'].iloc[j] != lastid:
                    drop_thingy.append(i)
                    i+=1
            else:
                last = thingy_dataframe['T_activity_code'].iloc[i]
                lastid = thingy_dataframe['T_subject_id'].iloc[i]
                i+=1
                j+=1
        dataframe = pd.concat([thingy_dataframe.drop(drop_thingy).reset_index(drop=True), respeck_dataframe.drop(drop_respeck).reset_index(drop=True)], axis=1)
        dataframe = dataframe.dropna()
    elif respeck:
        dataframe = respeck_dataframe
    elif thingy:
        dataframe = thingy_dataframe
    else:
        print("No file was found.")
        return pd.DataFrame(), False, False
    print("File(s) loaded.")
    return dataframe, thingy, respeck
         
def create_dataset(dataframe, data_columns, label_column, time_steps=50, step=10):
    XX, YY = [], []
    raw_x = dataframe[data_columns]
    raw_y = dataframe[label_column]
    
    for i in range(0, len(raw_x) - time_steps, step):
        label = raw_y.iloc[i].values
        value = raw_x.iloc[i]
        same = True
        for j in range(i + 1, i + time_steps):
            if label != raw_y.iloc[j].values:
                same = False
                break
        if not same:
            continue
        else:
            value = raw_x.iloc[i: i + time_steps]
        XX.append(value)
        YY.append(label)
        
    X = np.array(XX)
    Y = np.array(YY)
    return X, Y
      
def plot_cm(y_true, y_pred, class_names, respeck, thingy):
    cm = confusion_matrix(y_true, y_pred, labels=class_names, normalize='true')
    register_matplotlib_converters()
    sns.set(style='whitegrid', palette='muted', font_scale=1)
    fig, ax = plt.subplots(figsize=(12, 10)) 
    rcParams['figure.dpi'] = 300
    ax = sns.heatmap(
        cm, 
        annot=True, 
        fmt=".2f", 
        cmap=sns.diverging_palette(220, 20, n=7),
        ax=ax
    )
    plt.xticks(rotation=70)
    plt.yticks(rotation=90)
    plt.ylabel('Actual')
    plt.xlabel('Predicted')
    ax.set_xticklabels(class_names)
    ax.set_yticklabels(class_names, rotation=0)
    b, t = plt.ylim()
    b += 0.5
    t -= 0.5
    plt.ylim(b, t)
    if respeck and thingy:
        plt.savefig('confusion_matrix.png') 
    elif respeck:
        plt.savefig('confusion_matrix_respeck.png')
    elif thingy:
        plt.savefig('confusion_matrix_thingy.png')
        
if __name__ == "__main__":       

    my_parser = argparse.ArgumentParser()
    my_parser.add_argument('--model_path', action='store', type=str, required=True)
    my_parser.add_argument('--thingy_file', action='store', type=str, default=None)
    my_parser.add_argument('--respeck_file', action='store', type=str, default=None)
    args = my_parser.parse_args()
    
    dataframe, thingy, respeck = csv_to_dataframe(args.thingy_file, args.respeck_file)
    if dataframe.empty:
        print("Please provide a correct file path.")
        exit()
    
    Thingy_columns = ['T_accel_x', 'T_accel_y', 'T_accel_z', 'T_gyro_x', 'T_gyro_y', 'T_gyro_z', 'mag_x', 'mag_y', 'mag_z']
    Thingy_label = ['T_activity_type']
    Respect_columns = ['R_accel_x', 'R_accel_y', 'R_accel_z', 'R_gyro_x', 'R_gyro_y', 'R_gyro_z']
    Respect_label = ['R_activity_type']
    label = np.array([['Sitting'], ['Walking at normal speed'], ['Lying down on back'], ['Sitting bent forward'], ['Sitting bent backward'], ['Lying down right'],
                    ['Lying down left'], ['Lying down on stomach'], ['Movement'], ['Running'], ['Climbing stairs'], ['Descending stairs'], ['Desk work'], ['Standing']])
    
    if thingy and respeck:
        X, y = create_dataset(dataframe, Thingy_columns + Respect_columns, Respect_label, 50, 10)
    elif thingy:
        X, y = create_dataset(dataframe, Thingy_columns, Thingy_label, 50, 10)
    elif respeck:
        X, y = create_dataset(dataframe, Respect_columns, Respect_label, 50, 10) 
    else:
        print("No data was found.")
        exit()
    
    
    try:
        interpreter = tf.lite.Interpreter(model_path=args.model_path)
    except:
        print("Model was not found.")
        exit()
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    input_data = np.array([X[0]]).astype(np.float32)
    
    interpreter.set_tensor(input_details[0]['index'], input_data)
    interpreter.invoke()
    y_pred = interpreter.get_tensor(output_details[0]['index'])
    
    number_of_data = X.shape[0]
    for i in range(1, number_of_data):
        input_data = np.array([X[i]]).astype(np.float32)

        interpreter.set_tensor(input_details[0]['index'], input_data)
        interpreter.invoke()
        output_data = interpreter.get_tensor(output_details[0]['index'])
        y_pred = np.append(y_pred, output_data, axis=0)

    encoder = OneHotEncoder(handle_unknown='ignore', sparse=False)
    encoder.fit(label)
    y_pred_binary = encoder.inverse_transform(y_pred)
    report = classification_report(y, y_pred_binary, output_dict=True)
    print(classification_report(y, y_pred_binary))

    #print(report)

    plot_cm(y, y_pred_binary, encoder.categories_[0], respeck, thingy)
