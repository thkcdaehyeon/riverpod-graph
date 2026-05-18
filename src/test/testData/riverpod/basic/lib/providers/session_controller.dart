import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'session_controller.g.dart';

@riverpod
class SessionController extends _$SessionController {
  @override
  String build() => 'signed-out';

  void signIn(String name) {
    state = name;
  }
}
