#!/usr/bin/perl 

# bigbench template file. This runs the benchmark suite under Perl w/ some hackery

$| = 1;
use Benchmark;

# output header and test for correct version

print "Perl v0.00";

$cf = 'main';
$c = 'main';

sub new { $_[1]; }

sub copy { $_[0] };

my ($x,$y,$z);
# actual benchmarking code will be appended

