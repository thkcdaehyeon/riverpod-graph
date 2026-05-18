import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'user_providers.g.dart';

class User {
  const User(this.name);

  final String name;
}

class Profile {
  const Profile(this.user);

  final User user;
}

@riverpod
String currentUser(Ref ref) => 'guest';

@Riverpod(keepAlive: true)
Future<User> user(Ref ref, String id) async => User(id);

@riverpod
Profile profile(Ref ref) {
  final user = ref.watch(userProvider('42')).requireValue;
  return Profile(user);
}

@riverpod
String selectedUserName(Ref ref) {
  final selected = ref.watch(userProvider('42').select((value) => value.valueOrNull?.name));
  final future = ref.watch(userProvider('42').future);
  return '$selected $future';
}
