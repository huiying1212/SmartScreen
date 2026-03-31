import sys
sys.path.append('.')
from scripts.run_ablation import *
import glob

def run_tuning():
    files = glob.glob('app/test_data/wisdm-dataset/raw/phone/accel/*.txt')
    chunks = parse_data(files, max_users=15)
    
    baseline = evaluate_config(chunks, False, False)
    print("Baseline:", baseline)
    
    # We will try different smoothing/penalty params by overriding functions
    best_flicker = baseline['flickers_per_min']
    
    def evaluate_tuned(chunks, decay, max_ratio_dist):
        correct_count = 0
        total_count = 0
        transitions = 0
        total_conf = 0.0
        
        for chunk in chunks:
            history = []
            last_activity = None
            
            for win in chunk:
                # Classify with new rule
                def traverse(node, features, currentConfidence):
                    if node['isLeaf']:
                        return node['label'], currentConfidence * node['conf']
                    val = features[node['feat']]
                    thresh = node['thresh']
                    
                    # NEW PENALTY: relative distance, scaled
                    rel_dist = abs(val - thresh) / (thresh if thresh != 0 else 1.0)
                    dist_ratio = min(rel_dist / max_ratio_dist, 1.0)
                    penalty = 0.5 + 0.5 * dist_ratio  # Drops down to 0.5 if very close
                    
                    next_node = node['left'] if val < thresh else node['right']
                    return traverse(next_node, features, currentConfidence * penalty)
                
                stationary = {'isLeaf': True, 'label': 'stationary', 'conf': 0.95}
                driving = {'isLeaf': True, 'label': 'driving', 'conf': 0.80}
                cycling = {'isLeaf': True, 'label': 'cycling', 'conf': 0.78}
                walking = {'isLeaf': True, 'label': 'walking', 'conf': 0.90}
                running = {'isLeaf': True, 'label': 'running', 'conf': 0.88}
                
                vehicleOrCycle = {'isLeaf': False, 'feat': 'variance', 'thresh': 2.5, 'left': driving, 'right': cycling}
                walkOrRun = {'isLeaf': False, 'feat': 'variance', 'thresh': 12.0, 'left': walking, 'right': running}
                moving = {'isLeaf': False, 'feat': 'peak_freq', 'thresh': 0.8, 'left': vehicleOrCycle, 'right': walkOrRun}
                root = {'isLeaf': False, 'feat': 'variance', 'thresh': 0.3, 'left': stationary, 'right': moving}
                
                raw_act, conf = traverse(root, {'variance': win['variance'], 'peak_freq': win['peak_freq']}, 1.0)
                
                # NEW SMOOTHING
                history.append({'activity': raw_act, 'confidence': conf})
                if len(history) > 3:
                     history = history[-3:]
                
                scores = {}
                weight = 1.0
                for pred in reversed(history):
                    scores[pred['activity']] = scores.get(pred['activity'], 0.0) + pred['confidence'] * weight
                    weight *= decay
                
                final_act = max(scores.items(), key=lambda x: x[1])[0]
                
                total_conf += conf
                total_count += 1
                if final_act == win['true_label']:
                    correct_count += 1
                    
                if last_activity is not None and final_act != last_activity:
                    transitions += 1
                    
                last_activity = final_act
                
        duration_minutes = (total_count * 1.0) / 60.0
        return {
            'accuracy': (correct_count / total_count * 100),
            'flickers_per_min': (transitions / duration_minutes),
        }

    for decay in [0.6, 0.7, 0.8]:
        for max_ratio_dist in [0.2, 0.5, 1.0]:
            res = evaluate_tuned(chunks, decay, max_ratio_dist)
            print(f"Decay: {decay}, MaxRatio: {max_ratio_dist} -> Acc: {res['accuracy']:.2f}, Flickers: {res['flickers_per_min']:.3f}")

if __name__ == '__main__':
    run_tuning()
