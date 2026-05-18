import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../providers/session_controller.dart';
import '../../providers/user_providers.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final name = ref.watch(currentUserProvider);
    final selectedName = ref.watch(userProvider('42').select((value) => value.valueOrNull?.name));
    final session = ref.watch(sessionControllerProvider.notifier);
    final profile = ref.watch(profileProvider);

    return Text('$name $selectedName $session $profile');
  }
}
