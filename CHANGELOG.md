
## [v0.0.9](https://github.com/ffalt/doughtime/compare/v0.0.9) (2026-08-20)

### Features

 -  **alarm**  implement full-screen intent for timer alarms and add permission checks ([b8b7fd83a3aecf4](https://github.com/ffalt/doughtime/commit/b8b7fd83a3aecf477e8e2f89b26d7b7cdb87ced6))

### Bug Fixes

 -  **android**  drop support for sdk < 27 ([0c56c1cd4b29a01](https://github.com/ffalt/doughtime/commit/0c56c1cd4b29a0158ed8679a15baf4a539cdfac0))
 -  **notification**  2nd try; open timer activity on notification click ([b2fa1b84e89d81e](https://github.com/ffalt/doughtime/commit/b2fa1b84e89d81ee3fd075bce32704c3d9830783))

## [v0.0.8](https://github.com/ffalt/doughtime/compare/v0.0.8) (2026-08-10)

### Features

 -  **notification**  show rest time in notification message ([4c37143cdb115b1](https://github.com/ffalt/doughtime/commit/4c37143cdb115b1acac3f99ed6f2022f7755c9a4))

### Bug Fixes

 -  **notification**  open timer activity on notification click ([721fbf9e971400d](https://github.com/ffalt/doughtime/commit/721fbf9e971400d25bc2d566aab1f28b36025210))

## [v0.0.7](https://github.com/ffalt/doughtime/compare/v0.0.7) (2026-08-10)

### Features

 -  **timer**  always show time decrease button ([6e41f3c869a1a94](https://github.com/ffalt/doughtime/commit/6e41f3c869a1a94a896bc2b73b0a7d61d3657c33))

### Bug Fixes

 -  **notification**  open activity on notification click ([d90b5341c8d7a49](https://github.com/ffalt/doughtime/commit/d90b5341c8d7a49c769a24199e1eb786338b8670))

## [v0.0.6](https://github.com/ffalt/doughtime/compare/v0.0.6) (2026-08-06)


### Bug Fixes

 -  **active timer**  when timer is done, allow increasing the current time and start again ([eaadb6d89c1fd1b](https://github.com/ffalt/doughtime/commit/eaadb6d89c1fd1b4c538ea5e8fa591a2e6d45179))

## [v0.0.5](https://github.com/ffalt/doughtime/compare/v0.0.5) (2026-07-31)


### Bug Fixes

 -  **active timer**  update step end calculations on increase/decrease timer duration ([16da37a188e4e5b](https://github.com/ffalt/doughtime/commit/16da37a188e4e5b9953a6267f27f42d0d3fafb9d))

## [v0.0.4](https://github.com/ffalt/doughtime/compare/v0.0.4) (2026-07-31)

### Features

 -  **step**  on the last step, button text is "Close" not "Next" ([0b5a84673df1dad](https://github.com/ffalt/doughtime/commit/0b5a84673df1dade7d9e7653473c4cf677603a6c))
 -  **timer**  allow open the timer without starting it ([1eef838fa6bcbf2](https://github.com/ffalt/doughtime/commit/1eef838fa6bcbf26d35fd1fb94a42e19e8311856))

### Bug Fixes

 -  **step**  show - for empty titles ([a63ffc69b61f261](https://github.com/ffalt/doughtime/commit/a63ffc69b61f2612e5f21969dd2e8d73fecc5d82))
 -  **timer run**  adjust button margin ([41a2b3c022ec704](https://github.com/ffalt/doughtime/commit/41a2b3c022ec70463a75ce88c79f256a86f60427))
 -  **timer edit**  do not scroll add step button ([1e9782ce6b525d1](https://github.com/ffalt/doughtime/commit/1e9782ce6b525d17d17893094f33e9fd5bb8ccdc))

## [v0.0.3](https://github.com/ffalt/doughtime/compare/v0.0.3) (2026-07-31)

### Features

 -  **edit**  add a duration picker ([854dba38e1582dd](https://github.com/ffalt/doughtime/commit/854dba38e1582ddbe9a891419973e93e71c574fb))
 -  **edit**  support drag & drop reordering ([6ffab4a4b677ae5](https://github.com/ffalt/doughtime/commit/6ffab4a4b677ae59281b5f7dc05b159ce59f8db1))
 -  **main screen**  add a label above the active timer list ([94884ae710f405c](https://github.com/ffalt/doughtime/commit/94884ae710f405c4d6f942fa59521c122ab0c751))
 -  **alarm**  stop alarm playing on click on notification and active timer entry ([b13937dfd600a51](https://github.com/ffalt/doughtime/commit/b13937dfd600a510e11d92d3efc49d2231c8ff5f))
 -  **step list**  allow switching to steps in the step preview list ([383bbd5d6a2dfee](https://github.com/ffalt/doughtime/commit/383bbd5d6a2dfee3659f2dea8c601c0913bced0e))
 -  **timer buttons**  move all timer buttons below the clock ([9a47024a1ae23a8](https://github.com/ffalt/doughtime/commit/9a47024a1ae23a81ff7066ddff632039fe8e7c72))
 -  **timer buttons**  add +/- timer adjustment buttons with long press for quick changes ([37642321dc871c8](https://github.com/ffalt/doughtime/commit/37642321dc871c864c3e8e2b9dc51fe3661b9140))
 -  **step list**  always show all steps in active timer, mark active one ([8aa168b0e056e01](https://github.com/ffalt/doughtime/commit/8aa168b0e056e0198be9eb74792b86d3b1147f9c))

### Bug Fixes

 -  **alarm**  ensure an actual alarm sound is playing, add vibrate ([c2ce89041381be0](https://github.com/ffalt/doughtime/commit/c2ce89041381be052fa173ac99f4c99cb3dd88c7))

## [v0.0.2](https://github.com/ffalt/doughtime/compare/v0.0.2) (2026-07-26)


### Bug Fixes

 -  **build**  use ExecOperations for git commands ([654e9ee338b065a](https://github.com/ffalt/doughtime/commit/654e9ee338b065ab64581fc754f6860a2f23a82c))
 -  **android**  do not use forced edge-to-edge screens ([50985e6b7d2ac17](https://github.com/ffalt/doughtime/commit/50985e6b7d2ac17aed47e9add268972a138edd60))

## [v0.0.1] (2026-06-15)

initial version
