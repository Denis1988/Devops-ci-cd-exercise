from flask import Blueprint, request, jsonify

user_bp = Blueprint('users', __name__)

# Move the data OUTSIDE the functions so it stays in memory
USERS = {
    1: {'id': 1, 'name': 'John Doe', 'email': 'john@example.com'},
    2: {'id': 2, 'name': 'Jane Smith', 'email': 'jane@example.com'}
}

@user_bp.route('/', methods=['GET'])
def get_users():
    return jsonify(list(USERS.values()))

@user_bp.route('/<int:user_id>', methods=['GET'])
def get_user(user_id):
    # Now it checks the global USERS dictionary
    user = USERS.get(user_id)
    if user:
        return jsonify(user)
    return jsonify({'error': 'User not found'}), 404

@user_bp.route('/', methods=['POST'])
def create_user():
    data = request.get_json()
    if not data or 'name' not in data or 'email' not in data:
        return jsonify({'error': 'Name and email are required'}), 400
    
    # Generate a new ID based on the current size
    new_id = max(USERS.keys()) + 1
    new_user = {'id': new_id, 'name': data['name'], 'email': data['email']}
    
    # CRITICAL: Save the user to the global dictionary
    USERS[new_id] = new_user
    
    return jsonify(new_user), 201